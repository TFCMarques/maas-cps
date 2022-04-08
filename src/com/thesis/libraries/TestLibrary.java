package com.thesis.libraries;

import jade.core.Agent;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thesis.utilities.Constants;

public class TestLibrary implements IResource {

    private Agent myAgent;

    @Override
    public void init(Agent myAgent) {
        this.myAgent = myAgent;
        // System.out.println("Test library has been successfully initialized for agent: " + myAgent.getLocalName());
    }

    @Override
    public boolean executeSkill(String skillID) {
        try {
            switch (skillID) {
                case Constants.SK_GLUE:
                case Constants.SK_CUT_SQUARE: {
                    Thread.sleep(2000);
                    return true;
                }
                case Constants.SK_CUT_CIRCLE: {
                    Thread.sleep(3000);
                    return true;
                }
                case Constants.SK_CUT_TRIANGLE: {
                    Thread.sleep(1000);
                    return true;
                }
                case Constants.SK_PICK_UP:
                case Constants.SK_DROP:
                    Thread.sleep(1000);
                    return true;
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(TestLibrary.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    public String[] getSkills(String type) {
        String[] skills;

        switch (type) {
            case "GlueStation":
                skills = new String[1];
                skills[0] = Constants.SK_GLUE;
                return skills;
            case "CuttingStation":
                skills = new String[3];
                skills[0] = Constants.SK_CUT_TRIANGLE;
                skills[1] = Constants.SK_CUT_SQUARE;
                skills[2] = Constants.SK_CUT_CIRCLE;
                return skills;
            case "Operator":
                skills = new String[2];
                skills[0] = Constants.SK_PICK_UP;
                skills[1] = Constants.SK_DROP;
                return skills;
        }
        return null;
    }
}
