package com.thesis.utilities;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;


public class HttpRequestUtil {
    public static void httpCallback(String url, String key, String value) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPut httpPut = new HttpPut(url);
            httpPut.setHeader("Accept", "application/json");
            httpPut.setHeader("Content-type", "application/json");

            JSONObject jsonBody = new JSONObject();
            jsonBody.put(key, value);

            StringEntity stringEntity = new StringEntity(jsonBody.toString());
            httpPut.setEntity(stringEntity);

            //System.out.println("Executing HTTP Request " + httpPut.getRequestLine());

            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if(status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            };

            httpClient.execute(httpPut, responseHandler);
        }
    }
}
