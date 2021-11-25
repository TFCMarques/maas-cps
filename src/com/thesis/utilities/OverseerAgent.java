package com.thesis.utilities;

import com.thesis.resource.ResourceAgent;
import com.thesis.transport.TransportAgent;
import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OverseerAgent extends Agent{
    WebServices myServices;
    int operators, glueStations, cuttingStations;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.operators = Integer.parseInt((String) args[0]);
        this.glueStations = Integer.parseInt((String) args[1]);
        this.cuttingStations = Integer.parseInt((String) args[2]);

        try {
            this.launchMultipleResources("Operator", "Operator to pick and drop products", this.operators);
            this.launchMultipleResources("GlueStation", "Station to apply various types of glue to a product", this.glueStations);
            this.launchMultipleResources("CuttingStation", "Station to cut product into a shape", this.cuttingStations);
            this.launchTransport("AGV", "Operator to transport products");

            this.myServices = new WebServices(this);
            this.myServices.server.start();
            System.out.println("Initialized Web Server");

        } catch (IOException | StaleProxyException ex) {
            Logger.getLogger(OverseerAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void launchResource(String name, String type, String description) throws StaleProxyException {
        ResourceAgent newResource = new ResourceAgent();
        newResource.setArguments(new Object[]{name, type, description, "com.thesis.libraries.TestLibrary", name});
        AgentController agent = this.getContainerController().acceptNewAgent(name, newResource);
        agent.start();
    }

    private void launchMultipleResources(String type, String description, int amount) throws StaleProxyException {
        for(int i = 0; i < amount; i++) {
            launchResource(String.format("%s%d", type, i+1), type, description);
        }
    }

    private void launchTransport(String name, String description) throws StaleProxyException {
        TransportAgent newTransport = new TransportAgent();
        newTransport.setArguments(new Object[]{name, description, "com.thesis.libraries.TestTransportLibrary"});
        AgentController agent = this.getContainerController().acceptNewAgent(name, newTransport);
        agent.start();
    }
}
