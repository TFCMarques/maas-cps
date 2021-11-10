/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.thesis.libraries;

import jade.core.Agent;

public interface ITransport {
    public void init(Agent myAgent);
    public String[] getSkills();
    public boolean executeMove(String origin, String destination, String ProductID);
}
