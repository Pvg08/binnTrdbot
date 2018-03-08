/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import java.util.List;
import org.ta4j.core.Decimal;
import org.ta4j.core.Order;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.analysis.criteria.LinearTransactionCostCriterion;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;

/**
 *
 * @author EVG_Adminer
 */
public class ProfitWithoutComissionCriterion extends AbstractAnalysisCriterion {

    private TotalProfitCriterion profit;
    private LinearTransactionCostCriterion transaction_cost;

    public ProfitWithoutComissionCriterion(double comission) {
        profit = new TotalProfitCriterion();
        transaction_cost = new LinearTransactionCostCriterion(1.0, comission, 0.0);
    }

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        double value = 1d;
        for (Trade trade : tradingRecord.getTrades()) {
            value *= calculateProfit(series, trade);
        }
        return value;
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return calculateProfit(series, trade);
    }
    
    private double calculateProfit(TimeSeries series, Trade trade) {
        double profit_base = profit.calculate(series, trade);
        double comission_base = transaction_cost.calculate(series, trade);
        return profit_base - comission_base;
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 > criterionValue2;
    }
    
    @Override
    public Strategy chooseBest(TimeSeriesManager manager, List<Strategy> strategies) {
        int checked_first = (int)(Math.random() * strategies.size());
        Strategy bestStrategy = strategies.get(checked_first);
        double bestCriterionValue = calculate(manager.getTimeSeries(), manager.run(bestStrategy));
        for (int i = 0; i < strategies.size(); i++) {
            if (i!=checked_first) {
                Strategy currentStrategy = strategies.get(i);
                double currentCriterionValue = calculate(manager.getTimeSeries(), manager.run(currentStrategy));
                if (betterThan(currentCriterionValue, bestCriterionValue)) {
                    bestStrategy = currentStrategy;
                    bestCriterionValue = currentCriterionValue;
                }
            }
        }
        return bestStrategy;
    }
}
