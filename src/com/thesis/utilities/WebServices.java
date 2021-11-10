package com.thesis.utilities;

import java.io.*;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.thesis.product.ProductAgent;
import com.thesis.product.CustomProductAgent;

import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebServices extends Agent {
    private Agent myAgent;
    public HttpServer server;

    public WebServices(Agent a) throws IOException {
        this.myAgent = a;
        this.server = HttpServer.create(new InetSocketAddress(8081), 0);
        this.server.createContext("/test", new TestHandler());
        this.server.createContext("/product/A", new LaunchProductA());
        this.server.createContext("/product/B", new LaunchProductB());
        this.server.createContext("/product/C", new LaunchProductC());
        this.server.createContext("/product/custom", new LaunchCustomProduct());
        server.setExecutor(null);
    }

    class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                InputStream body = t.getRequestBody();
                JSONObject jsonBody = parseInputStream(body);
                String response = "This is the response with the test: " + jsonBody.get("test");

                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                t.sendResponseHeaders(200, response.length());

                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (JSONException ex) {
                Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, null, ex);
                JSONObject response = new JSONObject();
                response.put("message", "Bad Request");
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                t.sendResponseHeaders(400, response.toString().length());
                OutputStream os = t.getResponseBody();
                os.write(response.toString().getBytes());
                os.close();
            }
        }
    }

    class LaunchProductA implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                InputStream body = t.getRequestBody();
                JSONObject jsonBody = parseInputStream(body);

                String runId = jsonBody.getString("runId");
                String statusCallback = jsonBody.getString("statusCallback");
                String logCallback = jsonBody.getString("logCallback");

                launchProduct(runId, "A", statusCallback, logCallback);
            } catch (StaleProxyException | JSONException ex) {
                Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, null, ex);
            }

            String response = "Launched product of type A";
            t.getResponseHeaders().add("Access-Control-Allow-Origin","*");
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    class LaunchProductB implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                InputStream body = t.getRequestBody();
                JSONObject jsonBody = parseInputStream(body);

                String runId = jsonBody.getString("runId");
                String statusCallback = jsonBody.getString("statusCallback");
                String logCallback = jsonBody.getString("logCallback");

                launchProduct(runId, "B", statusCallback, logCallback);
            } catch (StaleProxyException ex) {
                Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, null, ex);
            }

            String response = "Launched product of type B";
            t.getResponseHeaders().add("Access-Control-Allow-Origin","*");
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    class LaunchProductC implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                InputStream body = t.getRequestBody();
                JSONObject jsonBody = parseInputStream(body);

                String runId = jsonBody.getString("runId");
                String statusCallback = jsonBody.getString("statusCallback");
                String logCallback = jsonBody.getString("logCallback");

                launchProduct(runId, "C", statusCallback, logCallback);
            } catch (StaleProxyException ex) {
                Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, null, ex);
            }

            String response = "Launched product of type C";
            t.getResponseHeaders().add("Access-Control-Allow-Origin","*");
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    class LaunchCustomProduct implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            InputStream body = t.getRequestBody();
            JSONObject jsonBody = parseInputStream(body);

            String runId = jsonBody.getString("runId");
            String statusCallback = jsonBody.getString("statusCallback");
            String logCallback = jsonBody.getString("logCallback");
            JSONArray jsonExecutionPlan = jsonBody.getJSONArray("executionPlan");
            ArrayList<String> executionPlan = new ArrayList<>();

            if(jsonExecutionPlan != null) {
                for(int i = 0; i < jsonExecutionPlan.length(); i++) {
                    executionPlan.add((String) jsonExecutionPlan.get(i));
                }
            }

            try {
                launchCustomProduct(runId, executionPlan, statusCallback, logCallback);
            } catch (StaleProxyException ex) {
                Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, null, ex);
            }

            String response = "Launched custom product";
            t.getResponseHeaders().add("Access-Control-Allow-Origin","*");
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private void launchProduct(String runId, String productType, String statusCallback, String logCallback) throws StaleProxyException {
        ProductAgent newProduct = new ProductAgent();
        newProduct.setArguments(new Object[]{runId, productType, statusCallback, logCallback});
        AgentController agent = this.myAgent.getContainerController().acceptNewAgent(runId, newProduct);
        agent.start();
    }

    private void launchCustomProduct(String runId, ArrayList<String> executionPlan, String statusCallback, String logCallback) throws StaleProxyException {
        CustomProductAgent newProduct = new CustomProductAgent();
        newProduct.setArguments(new Object[]{runId, executionPlan, statusCallback, logCallback});
        AgentController agent = this.myAgent.getContainerController().acceptNewAgent(runId, newProduct);
        agent.start();
    }

    private JSONObject parseInputStream(InputStream inputStream) {
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        JSONObject jsonObject = null;

        String currentLine;

        try {
            while((currentLine = streamReader.readLine()) != null) {
                stringBuilder.append(currentLine);
            }
            JSONTokener jsonTokener = new JSONTokener(stringBuilder.toString());
            jsonObject = new JSONObject(jsonTokener);
        } catch (IOException ex) {
            Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, "IOException", ex);
        }

        return jsonObject;
    }
}
