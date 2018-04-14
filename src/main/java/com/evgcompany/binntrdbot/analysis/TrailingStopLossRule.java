/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.ta4j.core.Decimal;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.AbstractRule;

/**
 * A trailing stop-loss rule.
 */
public class TrailingStopLossRule extends AbstractRule {

    /** The close price indicator */
    private final ClosePriceIndicator closePrice;
    
    /** The loss ratio threshold (e.g. 0.97 for 3%) */
    private final Decimal lossRatioThreshold;

    /**
     * Constructor.
     * @param closePrice the close price indicator
     * @param lossPercentage the loss percentage
     */
    public TrailingStopLossRule(ClosePriceIndicator closePrice, Decimal lossPercentage) {
        this.closePrice = closePrice;
        this.lossRatioThreshold = Decimal.HUNDRED.minus(lossPercentage).dividedBy(Decimal.HUNDRED);
    }

    private Decimal getMaxPrice(int entry_index, int last_index) {
        Decimal maxval = closePrice.getValue(last_index);
        for(int i = last_index - 1; i >= entry_index; i--) {
            if (closePrice.getValue(i).compareTo(maxval) > 0) {
                maxval = closePrice.getValue(i);
            }
        }
        return maxval;
    }
    
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no trade opened, no loss
        if (tradingRecord != null) {
            Trade currentTrade = tradingRecord.getCurrentTrade();
            if (currentTrade.isOpened()) {
                Decimal maxPrice = getMaxPrice(currentTrade.getEntry().getIndex(), index);
                Decimal currentPrice = closePrice.getValue(index);
                Decimal threshold = maxPrice.multipliedBy(lossRatioThreshold);
                if (currentTrade.getEntry().isBuy()) {
                    satisfied = currentPrice.isLessThanOrEqual(threshold);
                } else {
                    satisfied = currentPrice.isGreaterThanOrEqual(threshold);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
