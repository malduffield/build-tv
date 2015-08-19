package com.limpygnome.daemon.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.ConnectException;

/**
 * A reusable REST client.
 */
public class RestClient
{
    private String userAgent;
    private int bufferSize;

    public RestClient()
    {
        this.userAgent = null;
        this.bufferSize = -1;
    }

    public RestClient(String userAgent, int bufferSize)
    {
        this.userAgent = userAgent;
        this.bufferSize = bufferSize;
    }

    public HttpResponse executePost(String url, JSONObject jsonRoot) throws IOException, ConnectException
    {
        try
        {
            String json = jsonRoot.toJSONString();

            // Make request
            HttpClient httpClient = HttpClients.createMinimal();

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-type", "application/json");

            if (userAgent != null)
            {
                httpPost.setHeader("User-Agent", userAgent);
            }

            httpPost.setEntity(new StringEntity(json));

            return httpClient.execute(httpPost);
        }
        catch (ConnectException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IOException("Failed to make JSON post request to: " + url, e);
        }
    }

    public String executeStr(String url) throws IOException
    {
        HttpClient httpClient = HttpClients.createMinimal();

        HttpGet httpGet = new HttpGet(url);

        if (userAgent != null)
        {
            httpGet.setHeader("User-Agent", userAgent);
        }

        HttpResponse httpResponse = httpClient.execute(httpGet);
        return StreamUtil.readInputStream(httpResponse.getEntity().getContent(), bufferSize);
    }

    public JSONObject executeJson(String url) throws IOException
    {
        String response = executeStr(url);

        try
        {
            JSONParser jsonParser = new JSONParser();
            return (JSONObject) jsonParser.parse(response);
        }
        catch (ParseException e)
        {
            throw new RuntimeException("Failed to parse content [" + response.length() + " chars]: " + response, e);
        }
    }
}
