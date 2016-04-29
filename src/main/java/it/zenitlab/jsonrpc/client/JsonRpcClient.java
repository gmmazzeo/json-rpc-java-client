/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package it.zenitlab.jsonrpc.client;

import it.zenitlab.jsonrpc.commons.JsonRpcResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.zenitlab.jsonrpc.commons.JsonRpcError;
import it.zenitlab.jsonrpc.commons.JsonRpcException;
import it.zenitlab.jsonrpc.commons.JsonRpcArrayParamsRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 * @author massimo
 */
public class JsonRpcClient {

    URL url;
    String sessionKey;

    public JsonRpcClient(String urlS) throws MalformedURLException {
        this.url = new URL(urlS);
    }

    public JsonRpcClient(String url, String sessionKey) throws MalformedURLException {
        this.url = new URL(url);
        this.sessionKey = sessionKey;
    }

    public Object call(String methodName, Type type, Object... params) throws JsonRpcException {
        JsonRpcArrayParamsRequest req = new JsonRpcArrayParamsRequest();
        int id = (int) (Math.random() * Integer.MAX_VALUE);
        req.setId(id + "");
        req.setMethod(methodName);
        req.setSessionkey(sessionKey);
        Object[] p = new Object[params.length];
        System.arraycopy(params, 0, p, 0, params.length);
        req.setParams(p);

        HttpURLConnection conn = null;
        String risultato = "";
        Gson gson = new GsonBuilder().setDateFormat("dd/MM/yyyy HH:mm").create();
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");            
            String data = gson.toJson(req);
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();
            if (conn.getResponseCode()!=HttpURLConnection.HTTP_OK) {
                throw new JsonRpcException(JsonRpcError.HTTP_ERROR,conn.getResponseCode()+" "+conn.getResponseMessage(),"Connection error");
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));
            String output;
            while ((output = br.readLine()) != null) {
                risultato += output;
            }
        } catch (IOException e) {
            throw new JsonRpcException(JsonRpcException.IO_EXCEPTION, "IOException "+e.toString(), "IOException");
        }

        conn.disconnect();
        JsonParser jp = new JsonParser();
        //System.out.println(risultato);
        JsonElement e = jp.parse(risultato);
        JsonObject jo = e.getAsJsonObject();
        JsonRpcResponse res = new JsonRpcResponse();
        try {
            res.setId(jo.get("id").getAsNumber());
        } catch (Exception ex) {
            res.setId(jo.get("id").getAsString());
        }
        if (!jo.get("jsonrpc").getAsString().equals("2.0")) {
            throw new JsonRpcException(JsonRpcException.INVALID_JSONRPC_VERSION, "Invalid jsonrpc version - Found " + res.getJsonrpc() + " instead of 2.0","Comunication error");
        }
        if (jo.has("result")) {
            JsonElement jer = jo.get("result");
            if (type == null) {
                res.setResult(jer);
            } else {
                res.setResult(gson.fromJson(jer, type));
            }
        } else {
            res.setResult(null);
            JsonObject joe = jo.getAsJsonObject("error");
            String error = joe.toString();
            JsonRpcError jre = gson.fromJson(error, JsonRpcError.class);
            throw new JsonRpcException(jre.getCode(), jre.getDetailedMessage(), jre.getUserMessage());
        }
        return res.getResult();
    }
}
