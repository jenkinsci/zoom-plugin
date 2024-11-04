package io.jenkins.plugins.zoom;

import hudson.ProxyConfiguration;
import hudson.util.Secret;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class ZoomNotifyClient{

    private static CloseableHttpClient defaultHttpClient = HttpClients.createDefault();

    public static boolean notify(String url, Secret authToken, boolean jenkinsProxyUsed, String message) {
        return notify(url, authToken == null ? null : authToken.getPlainText(), jenkinsProxyUsed, message);
    }

    public static boolean notify(String url, String authToken, boolean jenkinsProxyUsed, String message) {
        boolean success = false;
        log.info("Send notification to {}, message: {}", url, message);
        if(url == null || url.isEmpty()){
            log.error("Invalid URL: {}", url);
            return success;
        }
        try {
            CloseableHttpResponse response = jenkinsProxyUsed
                    ? notifyWithProxy(url, authToken, message)
                    : notifyNoProxy(url, authToken, message);
            int responseCode = response.getStatusLine().getStatusCode();
            log.info("Response code: {}", responseCode);
            if(responseCode == HttpStatus.SC_OK){
                success = true;
            }
        } catch (IllegalArgumentException e1){
            log.error("Invalid URL: {}", url);
        } catch (IOException e2) {
            log.error("Error posting to Zoom, url: {}, message: {}", url, message, e2);
        }
        log.info("Notify success? {}", success);
        return success;
    }

    private static CloseableHttpResponse notifyWithProxy(String url, String authToken, String message) throws IOException {
        CloseableHttpResponse response = null;
        ProxyConfiguration proxyConfig = null;
        try {
            proxyConfig = ProxyConfiguration.load();
        } catch (IOException e) {
            log.error("Error while loading the proxy configuration");
        }
        if(proxyConfig != null
                && proxyConfig.name != null && !proxyConfig.name.isEmpty()
                && proxyConfig.port > 0
                && !isNoProxyHost(url, proxyConfig.getNoProxyHostPatterns())
        )
        {
            HttpClientBuilder builder = HttpClients.custom();
            HttpHost proxyHost = new HttpHost(proxyConfig.name, proxyConfig.port);
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxyHost);
            builder.setRoutePlanner(routePlanner);
            String username = proxyConfig.getUserName();
            if(username != null && !username.isEmpty()) {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope(proxyConfig.name, proxyConfig.port),
                        new UsernamePasswordCredentials(username, proxyConfig.getPassword()));
                builder.setDefaultCredentialsProvider(credsProvider);
            }
            CloseableHttpClient customHttpClient = builder.build();
            try {
                response = doPost(customHttpClient, url, authToken, message);
            }
            finally {
                customHttpClient.close();
            }
        }
        else {
            response = notifyNoProxy(url, authToken, message);
        }
        return response;
    }

    private static CloseableHttpResponse notifyNoProxy(String url, String authToken, String message) throws IOException {
        CloseableHttpResponse response = doPost(defaultHttpClient, url, authToken, message);
        return response;
    }

    private static CloseableHttpResponse doPost(CloseableHttpClient httpClient, String url, String authToken, String message) throws IOException {
        CloseableHttpResponse response = null;
        HttpPost httpPost = null;
        try {
            httpPost = decoratePost(new HttpPost(url), authToken, message);
            response = httpClient.execute(httpPost);
        } finally {
            if(httpPost != null){
                httpPost.releaseConnection();
            }
        }
        return response;
    }

    private static HttpPost decoratePost(HttpPost httpPost, String authToken, String message) {
        httpPost.setHeader("content-type", "application/json;charset=UTF-8");
        if(authToken != null && !authToken.isEmpty()){
            httpPost.setHeader("Authorization", authToken);
        }
        if(message != null && !message.isEmpty()){
            StringEntity stringEntity = new StringEntity(message, "UTF-8");
            stringEntity.setContentEncoding("UTF-8");
            httpPost.setEntity(stringEntity);
        }
        return httpPost;
    }

    private static boolean isNoProxyHost(String url, List<Pattern> noProxyHostPatterns) {
        boolean result = false;
        try {
            String host = new URL(url).getHost();
            for(Pattern pattern : noProxyHostPatterns) {
                if(pattern.matcher(host).matches()) {
                    result = true;
                    break;
                }
            }
        }
        catch(MalformedURLException e) {
            log.error("Invalid URL: {}", url);
        }
        return result;
    }

}
