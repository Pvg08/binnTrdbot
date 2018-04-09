/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.ProfitWithoutComissionCriterion;
import com.evgcompany.binntrdbot.analysis.StrategyConfig;
import com.evgcompany.binntrdbot.analysis.StrategyDatasetInitializer;
import com.evgcompany.binntrdbot.mainApplication;
import com.evgcompany.binntrdbot.tradeProfitsController;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.jfree.data.time.Second;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
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
    
    private static final DecimalFormat df6 = new DecimalFormat("0.######");
    
    public StrategyItem(StrategiesController controller) {
        this.controller = controller;
        this.profitsChecker = controller.getProfitsChecker();
        this.config = new StrategyConfig();
        StrategyName = "NoName";
    }
    
    abstract public Strategy buildStrategy(TimeSeries series);

    public Strategy buildStrategyWithBestParams(TimeSeries series) {
        if (config.getItems().size() > 0) {
            List<HashMap<String, BigDecimal>> variants = config.GetParamVariants();
            int max_index = -1;
            double max_profit = 0;

            TimeSeriesManager sliceManager = new TimeSeriesManager(series);
            AnalysisCriterion profitCriterion;
            if (profitsChecker != null)
                profitCriterion = new ProfitWithoutComissionCriterion(0.01f * profitsChecker.getTradeComissionPercent().doubleValue());
            else 
                profitCriterion = new TotalProfitCriterion();

            for(int i = 0; i < variants.size(); i++) {
                config.setParams(variants.get(i));
                Strategy strategy = buildStrategy(series);
                TradingRecord tradingRecordC = sliceManager.run(strategy);
                if (tradingRecordC.getTradeCount() > 0) {
                    double profit = profitCriterion.calculate(series, tradingRecordC);
                    if (profit > max_profit && profit != 1.0) {
                        max_profit = profit;
                        max_index = i;
                    }
                }
            }
            config.resetParams();
            if (max_index >= 0) {
                config.setParams(variants.get(max_index));
                mainApplication.getInstance().log("Optimal params for " + StrategyName + " is: " + StrategyConfig.ConfigRowToStr(variants.get(max_index)) + " ::: profit = " + df6.format(max_profit));
            }
        }
        
        return buildStrategy(series);
    }
    
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
        return StrategyName;
    }
    
    /**
     * @return the config
     */
    public StrategyConfig getConfig() {
        return config;
    }
}
