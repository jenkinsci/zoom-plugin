package io.jenkins.plugins.zoom;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URISyntaxException;

@Slf4j
public class ZoomNotifyClient{

    private static CloseableHttpClient httpclient = HttpClients.createDefault();

    public static boolean notify(String url, String authToken, String message) {
        boolean success = false;
        log.info("Send notification to {}, authToken: {}, message: {}", url, authToken, message);
        if(url == null || url.isEmpty()){
            log.error("Invalid URL: {}", url);
            return success;
        }
        HttpPost httpPost = null;
        try {
            httpPost = new HttpPost(url);
            httpPost.setHeader("content-type", "application/json;charset=UTF-8");
            if(authToken != null && !authToken.isEmpty()){
                httpPost.setHeader("Authorization", authToken);
            }
            if(message != null && !message.isEmpty()){
                StringEntity stringEntity = new StringEntity(message, "UTF-8");
                stringEntity.setContentEncoding("UTF-8");
                httpPost.setEntity(stringEntity);
            }
            CloseableHttpResponse response = httpclient.execute(httpPost);
            int responseCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            log.info("Response code: {}", responseCode);
            if (entity != null) {
                log.info("Response entity: {}", EntityUtils.toString(entity));
            }
            if(responseCode == HttpStatus.SC_OK){
                success = true;
            }
        } catch (IllegalArgumentException e1){
            log.error("Invalid URL: {}", url);
        } catch (IOException e2) {
            log.error("Error posting to Zoom, url: {}, authToken: {}, message: {}", url, authToken, message);
        } finally {
            if(httpPost != null){
                httpPost.releaseConnection();
            }
        }
        log.info("Notify success? {}", success);
        return success;
    }
}
