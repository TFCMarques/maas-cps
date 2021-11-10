package com.thesis.resource;

import jade.core.Agent;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.thesis.libraries.IResource;
import com.thesis.utilities.Constants;
import com.thesis.utilities.DFInteraction;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.proto.ContractNetResponder;

public class ResourceAgent extends Agent {
    String id;
    IResource myLib;
    String description;
    String[] associatedSkills;
    String location;
    boolean available;
    String skillToExecute;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.description = (String) args[1];

        //Load hw lib
        try {
            String className = (String) args[2];
            Class cls = Class.forName(className);
            Object instance;
            instance = cls.newInstance();
            myLib = (IResource) instance;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(ResourceAgent.class.getName()).log(Level.SEVERE, null, ex);
        }

        this.location = (String) args[3];
        this.available = true;

        myLib.init(this);
        this.associatedSkills = myLib.getSkills();
        System.out.println("Resource Deployed: " + this.id + " Executes: " + Arrays.toString(associatedSkills));

        //TO DO: Register in DF with the corresponding skills as services
        try {
            DFInteraction.RegisterInDF(this, this.associatedSkills, Constants.DFSERVICE_RESOURCE);
        } catch (FIPAException e) {
            System.out.println("Error registering " + this.id + " in DF");
        }

        // TO DO: Add responder behaviour/s
        this.addBehaviour(new CNResponder(this, MessageTemplate.MatchPerformative(ACLMessage.CFP)));
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
           //System.out.println("*** LOG: " + myAgent.getLocalName() + " received REQUEST from " + request.getSender().getLocalName());
           
           skillToExecute = request.getContent();
           
           ACLMessage agree = request.createReply();
           agree.setPerformative(ACLMessage.AGREE);
           //System.out.println("*** LOG: " + myAgent.getLocalName() + " sent AGREE to " + request.getSender().getLocalName());
           return agree;
       }
       
       @Override
       protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {
           boolean ok = myLib.executeSkill(skillToExecute);
           
           ACLMessage inform = request.createReply();
           inform.setPerformative(ACLMessage.INFORM);
           
           if (skillToExecute.equals("sk_q_c")) {
                if (ok)
                    inform.setContent("OK");
                else
                    inform.setContent("NOK");
                
                //System.out.println("*** LOG: " + myAgent.getLocalName() + " sent INFORM to " + request.getSender().getLocalName() + " with content = " + inform.getContent());     
           } else {
                //System.out.println("*** LOG: " + myAgent.getLocalName() + " sent INFORM to " + request.getSender().getLocalName());
           }
           
           available = true;

           return inform;
       }
    }
    
    // ************************* FIPA CONTRACTNET ******************************
    
    private class CNResponder extends ContractNetResponder {
        public CNResponder(Agent a, MessageTemplate mt) {
            super(a, mt);
        }
        
        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
            //System.out.println("*** LOG: " + myAgent.getLocalName() + " received CFP from " + cfp.getSender().getLocalName());
            
            ACLMessage reply = cfp.createReply();
            if (available) {
                reply.setPerformative(ACLMessage.PROPOSE);
                
                double rand = Math.random();
                reply.setContent("" + rand);
            
                //System.out.println("*** LOG: " + myAgent.getLocalName() + " sent PROPOSE to " + cfp.getSender().getLocalName() + " with content = " + rand);
            } else {
                reply.setPerformative(ACLMessage.REFUSE);
                //System.out.println("*** LOG: " + myAgent.getLocalName() + " sent REFUSE to " + cfp.getSender().getLocalName());
            }
            
            return reply;
        }
        
        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
            //System.out.println("*** LOG: " + myAgent.getLocalName() + " received ACCEPT-PROPOSAL from " + cfp.getSender().getLocalName());
            
            ACLMessage inform = cfp.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            inform.setContent(location);
            
            available = false;
            
            //System.out.println("*** LOG: " + myAgent.getLocalName() + " sent INFORM to " + cfp.getSender().getLocalName());
            return inform;
        }
        
    }
}