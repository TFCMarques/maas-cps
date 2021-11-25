package com.thesis.product;

import com.thesis.utilities.Constants;
import com.thesis.utilities.DFInteraction;
import com.thesis.utilities.HttpRequestUtil;
import com.thesis.utilities.WebServices;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;
import jade.proto.ContractNetInitiator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProductAgent extends Agent {

    String productId;
    String statusCallback;
    String logCallback;
    ArrayList<String> executionPlan = new ArrayList<>();

    private boolean negotiationDone, transportRequired, transportDone;
    private int currentStep;
    private int lastStep;
    private String currentLocation, nextLocation;
    private AID bestResource, transport;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.productId = (String) args[0];
        this.statusCallback = (String) args[1];
        this.logCallback = (String) args[2];
        this.executionPlan = (ArrayList<String>) args[3];

        httpLog(this.productId + " requires: " + executionPlan);
        httpStatus("Running");

        this.currentStep = 0;
        this.lastStep = executionPlan.size();
        this.currentLocation = "Source";

        this.negotiationDone = false;
        this.transportRequired = false;
        this.transportDone = false;

        SequentialBehaviour sb = new SequentialBehaviour();
        for (int i = 0; i < executionPlan.size(); i++) {
            sb.addSubBehaviour(new SearchResources(this));
            sb.addSubBehaviour(new TransportToResource(this));
            sb.addSubBehaviour(new ExecuteSkill(this));
            sb.addSubBehaviour(new FinishCurrentStep(this));
        }

        this.addBehaviour(sb);
    }

    @Override
    protected void takeDown() {
        super.takeDown();
    }

    private class REInitiatorTransport extends AchieveREInitiator {

        public REInitiatorTransport(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            httpLog(myAgent.getLocalName() + " received AGREE from " + agree.getSender().getLocalName());
        }

        @Override
        protected void handleRefuse(ACLMessage refuse) {
            httpLog(myAgent.getLocalName() + " received REFUSE from " + refuse.getSender().getLocalName());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE, null, ex);
            }

            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.setContent(currentLocation + Constants.TOKEN + nextLocation);
            request.addReceiver(transport);

            httpLog( myAgent.getLocalName() + " sent REQUEST to " + refuse.getSender().getLocalName());

            myAgent.addBehaviour(new ProductAgent.REInitiatorTransport(myAgent, request));
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            httpLog(myAgent.getLocalName() + " received INFORM from " + inform.getSender().getLocalName());
            currentLocation = nextLocation;
            transportDone = true;
        }
    }

    private class REInitiatorResource extends AchieveREInitiator {

        public REInitiatorResource(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            httpLog(myAgent.getLocalName() + " received AGREE from " + agree.getSender().getLocalName());
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            httpLog(myAgent.getLocalName() + " received INFORM from " + inform.getSender().getLocalName());
            if(currentStep == lastStep) {
                httpLog(myAgent.getLocalName() + " finished production");
                httpStatus("Finished");
            }
        }
    }

    private class CNInitiator extends ContractNetInitiator {

        public CNInitiator(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            double bestProposal = -1;
            AID bestProposer = null;
            ACLMessage accept = null;

            for (Object response : responses) {
                ACLMessage propose = (ACLMessage) response;
                if (propose.getPerformative() == ACLMessage.PROPOSE) {
                    httpLog(myAgent.getLocalName() + " received PROPOSE from " + propose.getSender().getLocalName() + " with content = " + propose.getContent());

                    ACLMessage reply = propose.createReply();
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    acceptances.add(reply);

                    double proposal = Double.parseDouble(propose.getContent());
                    if (proposal > bestProposal) {
                        bestProposal = proposal;
                        bestProposer = propose.getSender();
                        accept = reply;
                    }
                } else {
                    httpLog(myAgent.getLocalName() + " received REFUSE from " + propose.getSender().getLocalName());
                }
            }

            if (accept != null) {
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                httpLog(myAgent.getLocalName() + " sent ACCEPT-PROPOSAL to " + bestProposer.getLocalName());

                bestResource = bestProposer;
            } else { // no PROPOSE received
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE, null, ex);
                }

                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (Object response : responses) {
                    ACLMessage retry = (ACLMessage) response;
                    cfp.addReceiver(retry.getSender());
                    httpLog(myAgent.getLocalName() + " sent CFP to " + retry.getSender().getLocalName());
                }

                myAgent.addBehaviour(new ProductAgent.CNInitiator(myAgent, cfp));
            }
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            httpLog(myAgent.getLocalName() + " received INFORM from " + inform.getSender().getLocalName() + " with content = " + inform.getContent());

            nextLocation = inform.getContent();
            transportRequired = !currentLocation.equals(nextLocation);
            negotiationDone = true;
        }
    }

    private class SearchResources extends SimpleBehaviour {

        private boolean finished;

        public SearchResources(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            DFAgentDescription[] resourceAgents = null;
            try {
                httpLog("Current step: " + currentStep);
                httpLog(myAgent.getLocalName() + " searching DF for agent that can " + executionPlan.get(currentStep));
                resourceAgents = DFInteraction.SearchInDFByName(executionPlan.get(currentStep), myAgent);
            } catch (FIPAException e) {
                httpLog(myAgent.getLocalName() + " threw FIPAException searching DF for agents that " + executionPlan.get(currentStep));
            }

            if (resourceAgents != null) {
                httpLog(myAgent.getLocalName() + " found " + resourceAgents.length + " agents that can " + executionPlan.get(currentStep));

                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (DFAgentDescription agent : resourceAgents) {
                    cfp.addReceiver(agent.getName());
                    httpLog(myAgent.getLocalName() + " sent CFP to " + agent.getName().getLocalName());
                }

                this.finished = true;
                myAgent.addBehaviour(new ProductAgent.CNInitiator(myAgent, cfp));
            } else {
                httpLog(myAgent.getLocalName() + " couldn't find an agent that can " + executionPlan.get(currentStep));
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    private class SearchTransport extends SimpleBehaviour {

        private boolean finished;

        public SearchTransport(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            DFAgentDescription[] transportAgents = null;
            try {
                httpLog(myAgent.getLocalName() + " searching DF for transport agents");
                transportAgents = DFInteraction.SearchInDFByName("sk_move", myAgent);
            } catch (FIPAException e) {
                httpLog(myAgent.getLocalName() + " threw FIPAException searching DF for transport agents");
            }

            if (transportAgents != null) {
                httpLog(myAgent.getLocalName() + " found " + transportAgents.length + " transport agents");

                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.setContent(currentLocation + Constants.TOKEN + nextLocation);
                transport = transportAgents[0].getName();
                request.addReceiver(transport);

                httpLog(myAgent.getLocalName() + " sent REQUEST to " + transport.getLocalName());

                this.finished = true;
                myAgent.addBehaviour(new ProductAgent.REInitiatorTransport(myAgent, request));
            } else {
                httpLog(myAgent.getLocalName() + " coudln't find transport agents");
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    private class TransportToResource extends SimpleBehaviour {

        private boolean finished;

        public TransportToResource(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            if (negotiationDone) {
                if (transportRequired) {
                    myAgent.addBehaviour(new ProductAgent.SearchTransport(myAgent));

                    transportRequired = false;
                } else {
                    transportDone = true;
                }

                negotiationDone = false;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    private class ExecuteSkill extends SimpleBehaviour {

        private boolean finished;

        public ExecuteSkill(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            if (transportDone) {
                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.setContent(executionPlan.get(currentStep));
                request.addReceiver(bestResource);

                httpLog(myAgent.getLocalName() + " sent REQUEST to " + bestResource.getLocalName());
                myAgent.addBehaviour(new ProductAgent.REInitiatorResource(myAgent, request));

                transportDone = false;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    // **************************** WAIT SKILL *********************************
    private class FinishCurrentStep extends SimpleBehaviour {

        private boolean finished;

        public FinishCurrentStep(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            httpLog(myAgent.getLocalName() + " finished applying " + executionPlan.get(currentStep));
            if(currentStep != lastStep) {
                currentStep++;
            }

            this.finished = true;
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    private void httpLog(String logMessage) {
        String timestampedLogMessage = "[" + timestampLog() + "] " + logMessage;
        System.out.println(timestampedLogMessage);

        if(!logCallback.equals("")) {
            try {
                HttpRequestUtil.httpCallback(logCallback, "log", timestampedLogMessage);
            } catch (IOException ex) {
                Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void httpStatus(String statusMessage) {
        if(!statusCallback.equals("")) {
            try {
                HttpRequestUtil.httpCallback(statusCallback, "status", statusMessage);
            } catch (IOException ex) {
                Logger.getLogger(WebServices.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private String timestampLog() {
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss");
        return date.format(dateFormatter);
    }
}