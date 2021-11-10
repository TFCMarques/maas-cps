package com.thesis.product;

import com.thesis.utilities.Constants;
import com.thesis.utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;
import jade.proto.ContractNetInitiator;

import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProductAgent extends Agent {

    String id;
    ArrayList<String> executionPlan = new ArrayList<>();
    // TO DO: Add remaining attributes required for your implementation
    private boolean negotiationDone, transportRequired, transportDone, skillDone,
            productOK, repairRequired, repairDone;
    private int step;
    private String location, nextLocation;
    private AID bestResource, transport;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.executionPlan = this.getExecutionList((String) args[1]);
        System.out.println("*** LOG: " + this.id + " requires: " + executionPlan);

        this.step = 0;
        this.location = "Source";

        this.negotiationDone = false;
        this.transportRequired = false;
        this.transportDone = false;
        this.skillDone = false;
        this.productOK = true;
        this.repairRequired = false;
        this.repairDone = false;

        SequentialBehaviour sb = new SequentialBehaviour();
        for (int i = 0; i < executionPlan.size(); i++) {
            sb.addSubBehaviour(new SearchResources(this));
            sb.addSubBehaviour(new TransportToResource(this));
            sb.addSubBehaviour(new ExecuteSkill(this));
            sb.addSubBehaviour(new ProductRepair(this));
            sb.addSubBehaviour(new FinishRegularStep(this));
        }

        this.addBehaviour(sb);
    }

    @Override
    protected void takeDown() {
        super.takeDown(); //To change body of generated methods, choose Tools | Templates.
    }

    private ArrayList<String> getExecutionList(String productType) {
        switch (productType) {
            case "A":
                return Constants.PROD_A;
            case "B":
                return Constants.PROD_B;
            case "C":
                return Constants.PROD_C;
        }
        return null;
    }

    // *********************** FIPA REQUEST TRANSPORT **************************
    private class REInitiatorTransport extends AchieveREInitiator {

        public REInitiatorTransport(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println("*** LOG: " + myAgent.getLocalName() + " received AGREE from " + agree.getSender().getLocalName());
        }

        @Override
        protected void handleRefuse(ACLMessage refuse) {
            System.out.println("*** LOG: " + myAgent.getLocalName() + " received REFUSE from " + refuse.getSender().getLocalName());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE, null, ex);
            }

            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.setContent(location + Constants.TOKEN + nextLocation);
            request.addReceiver(transport);

            System.out.println("*** LOG: " + myAgent.getLocalName() + " sent REQUEST to " + refuse.getSender().getLocalName());

            myAgent.addBehaviour(new REInitiatorTransport(myAgent, request));
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("*** LOG: " + myAgent.getLocalName() + " received INFORM from " + inform.getSender().getLocalName());

            location = nextLocation;
            transportDone = true;
        }
    }

    // *********************** FIPA REQUEST RESOURCE ***************************
    private class REInitiatorResource extends AchieveREInitiator {

        public REInitiatorResource(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println("*** LOG: " + myAgent.getLocalName() + " received AGREE from " + agree.getSender().getLocalName());
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            if (executionPlan.get(step).equals("sk_q_c")) {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " received INFORM from " + inform.getSender().getLocalName() + " with content = " + inform.getContent());
                productOK = inform.getContent().equals("OK");
            } else {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " received INFORM from " + inform.getSender().getLocalName());
            }

            skillDone = true;
        }
    }

    // ************************ FIPA CONTRACTNET *******************************
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
                    System.out.println("*** LOG: " + myAgent.getLocalName() + " received PROPOSE from " + propose.getSender().getLocalName() + " with content = " + propose.getContent());

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
                    System.out.println("*** LOG: " + myAgent.getLocalName() + " received REFUSE from " + propose.getSender().getLocalName());
                }
            }

            if (accept != null) {
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                System.out.println("*** LOG: " + myAgent.getLocalName() + " sent ACCEPT-PROPOSAL to " + bestProposer.getLocalName());

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
                    System.out.println("*** LOG: " + myAgent.getLocalName() + " sent CFP to " + retry.getSender().getLocalName());
                }

                myAgent.addBehaviour(new CNInitiator(myAgent, cfp));
            }
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("*** LOG: " + myAgent.getLocalName() + " received INFORM from " + inform.getSender().getLocalName() + " with content = " + inform.getContent());

            nextLocation = inform.getContent();
            transportRequired = !location.equals(nextLocation);
            negotiationDone = true;
        }
    }

    // ************************* SEARCH RESOURCES ******************************
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
                System.out.println("*** LOG: Step = " + step);
                System.out.println("*** LOG: " + myAgent.getLocalName() + " searching DF for agent that can " + executionPlan.get(step));
                resourceAgents = DFInteraction.SearchInDFByName(executionPlan.get(step), myAgent);
            } catch (FIPAException e) {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " threw FIPAException searching DF for agents that " + executionPlan.get(step));
            }

            if (resourceAgents != null) {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " found " + resourceAgents.length + " agents that can " + executionPlan.get(step));

                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (DFAgentDescription agent : resourceAgents) {
                    cfp.addReceiver(agent.getName());
                    System.out.println("*** LOG: " + myAgent.getLocalName() + " sent CFP to " + agent.getName().getLocalName());
                }

                this.finished = true;
                myAgent.addBehaviour(new CNInitiator(myAgent, cfp));
            } else {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " couldn't find an agent that can " + executionPlan.get(step));
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    // ************************* SEARCH TRANSPORT ******************************
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
                System.out.println("*** LOG: " + myAgent.getLocalName() + " searching DF for transport agents");
                transportAgents = DFInteraction.SearchInDFByName("sk_move", myAgent);
            } catch (FIPAException e) {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " threw FIPAException searching DF for transport agents");
            }

            if (transportAgents != null) {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " found " + transportAgents.length + " transport agents");

                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.setContent(location + Constants.TOKEN + nextLocation);
                transport = transportAgents[0].getName();
                request.addReceiver(transport);

                System.out.println("*** LOG: " + myAgent.getLocalName() + " sent REQUEST to " + transport.getLocalName());

                this.finished = true;
                myAgent.addBehaviour(new REInitiatorTransport(myAgent, request));
            } else {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " coudln't find transport agents");
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    // ************************* WAIT NEGOTIATION ******************************
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
                    myAgent.addBehaviour(new SearchTransport(myAgent));

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

    // ************************** WAIT TRANSPORT *******************************
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
                request.setContent(executionPlan.get(step));
                request.addReceiver(bestResource);

                System.out.println("*** LOG: " + myAgent.getLocalName() + " sent REQUEST to " + bestResource.getLocalName());
                myAgent.addBehaviour(new REInitiatorResource(myAgent, request));

                transportDone = false;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    // **************************** WAIT RECOVERY ******************************
    private class ProductRepair extends SimpleBehaviour {

        private boolean finished;

        public ProductRepair(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            if (skillDone) {
                if (executionPlan.get(step).equals("sk_q_c")) {
                    if (productOK) {
                        System.out.println("*** LOG: " + myAgent.getLocalName() + " doesn't need repair");

                        repairRequired = false;
                        repairDone = true;
                    } else {
                        System.out.println("*** LOG: " + myAgent.getLocalName() + " needs repair\n");

                        step = 1;                   // go back to the first glue step
                        repairRequired = true;      // setup for FinishRepairStep

                        SequentialBehaviour sb = new SequentialBehaviour();
                        for (int i = 0; i < 3; i++) {
                            sb.addSubBehaviour(new SearchResources(myAgent));
                            sb.addSubBehaviour(new TransportToResource(myAgent));
                            sb.addSubBehaviour(new ExecuteSkill(myAgent));
                            sb.addSubBehaviour(new ProductRepair(myAgent));
                            sb.addSubBehaviour(new FinishRepairStep(myAgent));
                        }

                        myAgent.addBehaviour(sb);
                    }
                } else if (!repairRequired) {       // setup for FinishRegularStep
                    repairDone = true;
                }

                skillDone = false;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    // ************************ WAIT REPAIR SKILL ******************************
    private class FinishRepairStep extends SimpleBehaviour {

        private boolean finished;

        public FinishRepairStep(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            if (repairRequired) {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " finished applying " + executionPlan.get(step) + " as part of repairing\n");
                step++;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }

    // **************************** WAIT SKILL *********************************
    private class FinishRegularStep extends SimpleBehaviour {

        private boolean finished;

        public FinishRegularStep(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            if (repairDone) {
                System.out.println("*** LOG: " + myAgent.getLocalName() + " finished applying " + executionPlan.get(step) + "\n");
                step++;
                repairDone = false;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }
}
