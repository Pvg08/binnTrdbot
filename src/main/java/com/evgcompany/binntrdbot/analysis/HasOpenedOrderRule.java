/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.trading.rules.AbstractRule;

/**
 * A trailing stop-loss rule.
 */
public class HasOpenedOrderRule extends AbstractRule {

    private final boolean isBuy;
    
    /**
     * Constructor.
     */
    public HasOpenedOrderRule(boolean isBuy) {
        this.isBuy = isBuy;
    }
    public HasOpenedOrderRule() {
        this.isBuy = true;
    }
    
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        if (tradingRecord != null) {
            Trade currentTrade = tradingRecord.getCurrentTrade();
            if (currentTrade.isOpened()) {
                satisfied = currentTrade.getEntry().isBuy() == isBuy;
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
