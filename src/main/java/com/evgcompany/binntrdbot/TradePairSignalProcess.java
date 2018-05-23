/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.signal.SignalItem;
import com.evgcompany.binntrdbot.signal.TradeSignalProcessInterface;

/**
 *
 * @author EVG_adm_T
 */
public class TradePairSignalProcess extends TradePairStrategyProcess implements TradeSignalProcessInterface {
    
    protected SignalItem signalItem = null;
    
    public TradePairSignalProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
    }
    
    @Override
    public void setMainStrategy(String mainStrategy) {
        if (signalItem != null) {
            mainStrategy = "Signal";
        }
        if (!mainStrategy.equals("Signal")) {
            super.setMainStrategy(mainStrategy);
        } else {
            strategiesController.setMainStrategy(mainStrategy);
        }
    }
    
    @Override
    protected void beforeResetStrategies() {
        if (signalItem != null) {
            setMainStrategy("Signal");
            strategiesController.startSignal(signalItem);
        }
    }
    
    @Override
    public SignalItem getSignalItem() {
        return signalItem;
    }
    
    @Override
    public void setSignalItem(SignalItem item) {
        signalItem = item;
    }
}
