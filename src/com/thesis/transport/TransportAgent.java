package com.thesis.transport;

import com.thesis.resource.ResourceAgent;
import jade.core.Agent;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.thesis.libraries.ITransport;
import com.thesis.utilities.Constants;
import com.thesis.utilities.DFInteraction;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import java.util.StringTokenizer;

public class TransportAgent extends Agent {

    String id;
    ITransport myLib;
    String description;
    String[] associatedSkills;
    String source, destination;
    boolean available;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.description = (String) args[1];

        try {
            String className = (String) args[2];
            Class cls = Class.forName(className);
            Object instance;
            instance = cls.newInstance();
            myLib = (ITransport) instance;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(TransportAgent.class.getName()).log(Level.SEVERE, null, ex);
        }

        this.available = true;

        myLib.init(this);
        this.associatedSkills = myLib.getSkills();
        System.out.println("Transport Deployed: " + this.id + " Executes: " + Arrays.toString(associatedSkills));

        try {
            DFInteraction.RegisterInDF(this, this.associatedSkills, Constants.DFSERVICE_RESOURCE);
        } catch (FIPAException e) {
            System.out.println("Error registering " + this.id + " in DF");
        }

        this.addBehaviour(new REResponder(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST)));
    }

    @Override
    protected void takeDown() {
        super.takeDown();
    }

    // *************************** FIPA REQUEST ********************************
    private class REResponder extends AchieveREResponder {

        public REResponder(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {
            ACLMessage reply = request.createReply();
            if (available) {
                StringTokenizer st = new StringTokenizer(request.getContent(), Constants.TOKEN);
                source = st.nextToken();
                destination = st.nextToken();

                available = false;

                reply.setPerformative(ACLMessage.AGREE);
            } else {
                reply.setPerformative(ACLMessage.REFUSE);
            }

            return reply;
        }

        @Override
        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {
            myLib.executeMove(source, destination, id);
            ACLMessage inform = request.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            available = true;

            return inform;
        }
    }
}
