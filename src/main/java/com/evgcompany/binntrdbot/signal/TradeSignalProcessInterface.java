/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.signal;

/**
 *
 * @author EVG_adm_T
 */
public interface TradeSignalProcessInterface {
    public SignalItem getSignalItem();
    public void setSignalItem(SignalItem item);
}
