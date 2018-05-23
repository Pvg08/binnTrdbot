/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies.core;

/**
 *
 * @author EVG_adm_T
 */
public class OrderActionMarker {
    public OrderActionType action = OrderActionType.DO_NOTHING;
    public String label = "";
    public double timeStamp = 0;
    public double value = 0;
    
    public OrderActionMarker() {
        timeStamp = System.currentTimeMillis();
    }
    
    public OrderActionMarker(boolean isBuying, double price) {
        if (isBuying) {
            label = "BUY";
            action = OrderActionType.DO_BUY;
        } else {
            label = "SELL";
            action = OrderActionType.DO_SELL;
        }
        value = price;
        timeStamp = System.currentTimeMillis();
    }
}