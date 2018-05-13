/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

/**
 *
 * @author EVG_adm_T
 */
public interface ControllableOrderProcess {
    public void orderCancel();
    public void doBuy();
    public void doSell();
    public void doStop();
}
