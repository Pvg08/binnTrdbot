/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.ta4j.core.Decimal;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 *
 * @author EVG_adm_T
 */
public class TrailingStopLossIndicator extends CachedIndicator<Decimal> {

    private final Decimal lossRatioThreshold;
    private final Decimal minPrice;
    private final ClosePriceIndicator closePrice;
    private final TradingRecord trecord;

    public TrailingStopLossIndicator(ClosePriceIndicator closePrice, Decimal lossPercentage, Decimal minPrice, TradingRecord trecord) {
        super(closePrice.getTimeSeries());
        this.closePrice = closePrice;
        this.lossRatioThreshold = Decimal.HUNDRED.minus(lossPercentage).dividedBy(Decimal.HUNDRED);
        this.trecord = trecord;
        this.minPrice = minPrice;
    }

    private Decimal getMaxPrice(int entry_index, int last_index) {
        Decimal maxval = minPrice;
        for(int i = last_index - 1; i >= entry_index; i--) {
            if (closePrice.getValue(i).compareTo(maxval) > 0) {
                maxval = closePrice.getValue(i);
            }
        }
        return maxval;
    }
    
    @Override
    protected Decimal calculate(int index) {
        if (trecord != null) {
            Trade currentTrade = trecord.getCurrentTrade();
            if (currentTrade.isOpened() && currentTrade.getEntry().getIndex() < index) {
                Decimal maxPrice = getMaxPrice(currentTrade.getEntry().getIndex(), index);
                Decimal threshold = maxPrice.multipliedBy(lossRatioThreshold).max(minPrice);
                return threshold;
            }
        }
        return minPrice;
    }
}