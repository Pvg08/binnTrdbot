/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;

/**
 *
 * @author EVG_Adminer
 */
public class MaxDipCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        return calculate(series, tradingRecord.getLastTrade());
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        if (trade != null && trade.getEntry() != null && trade.getExit() != null) {
            return calculateMaximumDip(series, trade).doubleValue();
        }
        return 0;
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 < criterionValue2;
    }

    /**
     * Calculates the maximum dip over a series.
     * @param series the time series
     * @return the maximum dip over a series
     */
    private Decimal calculateMaximumDip(TimeSeries series, Trade trade) {
        Decimal maximumDip = Decimal.ZERO;
        if (!series.isEmpty() && trade != null) {
            Decimal enter = trade.getEntry().getPrice();
            maximumDip = enter;
            for (int i = trade.getEntry().getIndex(); i <= trade.getExit().getIndex(); i++) {
                Decimal value = series.getBar(i).getMinPrice();
                if (maximumDip.isGreaterThan(value)) {
                    maximumDip = value;
                }
            }
            if (maximumDip.isGreaterThan(Decimal.ZERO) && enter.isGreaterThan(maximumDip)) {
                maximumDip = enter.minus(maximumDip).dividedBy(enter).multipliedBy(Decimal.HUNDRED);
            } else {
                maximumDip = Decimal.ZERO;
            }
        }
        return maximumDip;
    }
}
