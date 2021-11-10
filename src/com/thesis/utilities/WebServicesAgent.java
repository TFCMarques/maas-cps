package com.thesis.utilities;

import jade.core.Agent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebServicesAgent extends Agent{
    WebServices myServices;

    @Override
    protected void setup() {
        try {
            this.myServices = new WebServices(this);
            this.myServices.server.start();
            System.out.println("Initialized Web Server");
        } catch (IOException ex) {
            Logger.getLogger(WebServicesAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
