/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.StrategyConfig;
import com.evgcompany.binntrdbot.analysis.StrategyDatasetInitializer;
import com.evgcompany.binntrdbot.tradeProfitsController;
import java.util.Date;
import org.jfree.data.time.Second;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.StopGainRule;
import org.ta4j.core.trading.rules.StopLossRule;

/**
 *
 * @author EVG_Adminer
 */
public abstract class StrategyItem {
    protected String StrategyName;
    protected StrategyDatasetInitializer initializer = null;
    protected tradeProfitsController profitsChecker = null;
    protected StrategiesController controller = null;
    protected StrategyConfig config = null;
    
    public StrategyItem(StrategiesController controller) {
        this.controller = controller;
        this.profitsChecker = controller.getProfitsChecker();
        this.config = new StrategyConfig();
        StrategyName = "NoName";
    }
    
    abstract public Strategy buildStrategy(TimeSeries series);

    protected Rule addStopLossGain(Rule rule, TimeSeries series) {
        if (profitsChecker != null) {
            /*if (profitsChecker.isLowHold()) {
                rule = rule.and(new StopGainRule(new ClosePriceIndicator(series), Decimal.valueOf(profitsChecker.getTradeMinProfitPercent())));
            }*/
            if (profitsChecker.getStopLossPercent() != null) {
                rule = rule.or(new StopLossRule(new ClosePriceIndicator(series), Decimal.valueOf(profitsChecker.getStopLossPercent())));
            }
            if (profitsChecker.getStopGainPercent() != null) {
                rule = rule.or(new StopGainRule(new ClosePriceIndicator(series), Decimal.valueOf(profitsChecker.getStopGainPercent())));
            }
        }
        return rule;
    }
    
    /**
     * Builds a JFreeChart time series from a Ta4j time series and an indicator.
     * @param barseries the ta4j time series
     * @param indicator the indicator
     * @param name the name of the chart time series
     * @return the JFreeChart time series
     */
    protected static org.jfree.data.time.TimeSeries buildChartTimeSeries(TimeSeries barseries, Indicator<Decimal> indicator, String name) {
        org.jfree.data.time.TimeSeries chartTimeSeries = new org.jfree.data.time.TimeSeries(name);
        for (int i = 0; i < barseries.getBarCount(); i++) {
            Bar bar = barseries.getBar(i);
            chartTimeSeries.add(new Second(Date.from(bar.getEndTime().toInstant())), indicator.getValue(i).doubleValue());
        }
        return chartTimeSeries;
    }
    
    /**
     * @return the initializer
     */
    public StrategyDatasetInitializer getInitializer() {
        return initializer;
    }

    /**
     * @return the StrategyName
     */
    public String getStrategyName() {
        return StrategyName + config.toString();
    }
    
    /**
     * @return the config
     */
    public StrategyConfig getConfig() {
        return config;
    }
}
