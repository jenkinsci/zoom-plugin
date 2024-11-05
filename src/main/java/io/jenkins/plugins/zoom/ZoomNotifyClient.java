package io.jenkins.plugins.zoom;

import hudson.ProxyConfiguration;
import hudson.util.Secret;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Client for sending notifications to Zoom webhook endpoints.
 * Supports both direct connections and proxy configurations.
 */
@Slf4j
public class ZoomNotifyClient {

    private static final int SOCKET_TIMEOUT = 30000;
    private static final int REQUEST_TIMEOUT = 10000;
    private static final int MAX_TOTAL_CONNECTIONS = 50;
    private static final int MAX_PER_ROUTE_CONNECTIONS = 10;
    private static final CloseableHttpClient DEFAULT_HTTP_CLIENT = createDefaultHttpClient();

    private ZoomNotifyClient() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Creates and configures the default HTTP client with SSL support and connection pooling
     */
    private static CloseableHttpClient createDefaultHttpClient() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();
            PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .setMaxConnTotal(MAX_TOTAL_CONNECTIONS)
                    .setMaxConnPerRoute(MAX_PER_ROUTE_CONNECTIONS)
                    .build();
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(REQUEST_TIMEOUT))
                    .setResponseTimeout(Timeout.ofMilliseconds(SOCKET_TIMEOUT))
                    .setCookieSpec(StandardCookieSpec.IGNORE)
                    .build();
            return HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            log.error("Failed to create default HTTP client", e);
            return HttpClients.createDefault();
        }
    }

    /**
     * Sends notification with Secret token
     */
    public static boolean notify(String url, Secret authToken, boolean jenkinsProxyUsed, String message) {
        return notify(url, authToken == null ? null : authToken.getPlainText(), jenkinsProxyUsed, message);
    }

    /**
     * Sends notification with plain text token
     */
    public static boolean notify(String url, String authToken, boolean jenkinsProxyUsed, String message) {
        log.info("Sending notification to URL: {}", url);
        log.debug("Proxy enabled: {}, Message length: {}", jenkinsProxyUsed, message != null ? message.length() : 0);
        if (!isValidUrl(url)) {
            log.error("Invalid URL provided: {}", url);
            return false;
        }
        try (CloseableHttpResponse response = jenkinsProxyUsed
                ? notifyWithProxy(url, authToken, message)
                : notifyNoProxy(url, authToken, message)) {
            if (response == null) {
                log.error("Received null response from server");
                return false;
            }
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.info("Response status: {}, body: {}", statusCode, responseBody);
            boolean success = statusCode == HttpStatus.SC_OK;
            log.info("Notification {} for URL: {}", success ? "succeeded" : "failed", url);
            return success;
        } catch (Exception e) {
            log.error("Failed to send notification to URL: {}", url, e);
            return false;
        }
    }

    /**
     * Sends notification using proxy configuration
     */
    private static CloseableHttpResponse notifyWithProxy(String url, String authToken, String message) throws IOException {
        ProxyConfiguration proxyConfig = getProxyConfiguration();
        if (!isProxyConfigValid(proxyConfig) || isNoProxyHost(url, proxyConfig.getNoProxyHostPatterns())) {
            log.info("Using direct connection - proxy not applicable for URL: {}", url);
            return notifyNoProxy(url, authToken, message);
        }
        log.info("Using proxy: {}:{}", proxyConfig.name, proxyConfig.port);
        try (CloseableHttpClient proxyClient = createProxyHttpClient(proxyConfig)) {
            return doPost(proxyClient, url, authToken, message);
        }
    }

    /**
     * Retrieves Jenkins proxy configuration
     */
    private static ProxyConfiguration getProxyConfiguration() {
        try {
            return ProxyConfiguration.load();
        } catch (IOException e) {
            log.error("Failed to load proxy configuration", e);
            return null;
        }
    }

    /**
     * Validates proxy configuration
     */
    private static boolean isProxyConfigValid(ProxyConfiguration config) {
        return config != null && config.name != null && !config.name.isEmpty() && config.port > 0;
    }

    /**
     * Creates HTTP client with proxy configuration
     */
    private static CloseableHttpClient createProxyHttpClient(ProxyConfiguration proxyConfig) {
        HttpHost proxyHost = new HttpHost(proxyConfig.name, proxyConfig.port);
        DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxyHost);
        HttpClientBuilder builder = HttpClients.custom().setRoutePlanner(routePlanner);
        String username = proxyConfig.getUserName();
        if (username != null && !username.isEmpty()) {
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(proxyHost),
                    new UsernamePasswordCredentials(username, proxyConfig.getPassword().toCharArray()));
            builder.setDefaultCredentialsProvider(credsProvider);
            log.debug("Proxy authentication configured");
        }
        return builder.build();
    }

    /**
     * Sends notification without proxy
     */
    private static CloseableHttpResponse notifyNoProxy(String url, String authToken, String message) throws IOException {
        log.debug("Sending notification without proxy");
        return doPost(DEFAULT_HTTP_CLIENT, url, authToken, message);
    }

    /**
     * Executes HTTP POST request
     */
    private static CloseableHttpResponse doPost(CloseableHttpClient httpClient, String url, String authToken, String message) throws IOException {
        Objects.requireNonNull(httpClient, "HTTP client must not be null");
        Objects.requireNonNull(url, "URL must not be null");
        HttpPost httpPost = new HttpPost(url);
        try {
            decoratePost(httpPost, authToken, message);
            log.debug("Executing POST request to URL: {}", url);
            return httpClient.execute(httpPost, HttpClientContext.create());
        } catch (IOException e) {
            log.error("Failed to execute POST request to URL: {}", url, e);
            throw e;
        }
    }

    /**
     * Decorates HTTP POST request with headers and body
     */
    private static void decoratePost(HttpPost httpPost, String authToken, String message) {
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        if (authToken != null && !authToken.isEmpty()) {
            httpPost.setHeader(HttpHeaders.AUTHORIZATION, authToken);
            log.debug("Authorization header set");
        }
        if (message != null && !message.isEmpty()) {
            StringEntity entity = new StringEntity(message, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);
            log.debug("Request body set, length: {}", message.length());
        }
    }

    /**
     * Checks if the given URL's host matches any of the no-proxy patterns
     *
     * @param url                 URL to check
     * @param noProxyHostPatterns List of patterns for no-proxy hosts
     * @return true if the host should not use a proxy, false otherwise
     */
    private static boolean isNoProxyHost(String url, List<Pattern> noProxyHostPatterns) {
        if (url == null || noProxyHostPatterns == null || noProxyHostPatterns.isEmpty()) {
            return false;
        }
        try {
            String host = new URL(url).getHost();
            return noProxyHostPatterns.stream().anyMatch(pattern -> pattern.matcher(host).matches());
        } catch (MalformedURLException e) {
            log.error("Invalid URL: {}", url, e);
            return false;
        }
    }

    /**
     * Validates the URL format
     *
     * @param url URL to validate
     * @return true if the URL is valid, false otherwise
     */
    private static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            log.error("Invalid URL: {}", url, e);
            return false;
        }
    }
}