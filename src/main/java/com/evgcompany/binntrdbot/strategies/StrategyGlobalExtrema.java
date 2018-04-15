/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.strategies.core.*;
import com.evgcompany.binntrdbot.analysis.*;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.indicators.helpers.MultiplierIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyGlobalExtrema extends StrategyItem {

    public StrategyGlobalExtrema(StrategiesController controller) {
        super(controller);
        StrategyName = "GlobalExtrema";
        config.Add("GlobalExtrema-Threshold", new StrategyConfigItem("0.001", "0.02", "0.001", "0.005"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, trecord, dataset) -> {
            long barSeconds = 60 * 60;
            if (tseries.getBarCount() > 0) {
                Bar first = tseries.getBar(tseries.getBeginIndex());
                barSeconds = (first.getEndTime().toInstant().toEpochMilli() - first.getBeginTime().toInstant().toEpochMilli() + 1) / 1000;
            }
            int bars_per_day = Math.round(60f * (60f / barSeconds) * 24f);
            if (bars_per_day < 2) bars_per_day = 2;
            ClosePriceIndicator closePrices = new ClosePriceIndicator(tseries);
            MaxPriceIndicator maxPrices = new MaxPriceIndicator(tseries);
            HighestValueIndicator dayMaxPrice = new HighestValueIndicator(maxPrices, bars_per_day);
            MinPriceIndicator minPrices = new MinPriceIndicator(tseries);
            LowestValueIndicator dayMinPrice = new LowestValueIndicator(minPrices, bars_per_day);
            dataset.addSeries(buildChartTimeSeries(tseries, dayMinPrice, "day Min"));
            dataset.addSeries(buildChartTimeSeries(tseries, dayMaxPrice, "day Max"));
        };
        
        double threshold = config.GetDoubleValue("GlobalExtrema-Threshold");
        
        long barSeconds = 60 * 60;
        if (series.getBarCount() > 0) {
            Bar first = series.getBar(series.getBeginIndex());
            barSeconds = (first.getEndTime().toInstant().toEpochMilli() - first.getBeginTime().toInstant().toEpochMilli() + 1) / 1000;
        }
        int bars_per_day = Math.round(60f * (60f / barSeconds) * 24f);
        if (bars_per_day < 2) bars_per_day = 2;
        ClosePriceIndicator closePrices = new ClosePriceIndicator(series);

        // Getting the max price over the past day
        MaxPriceIndicator maxPrices = new MaxPriceIndicator(series);
        HighestValueIndicator dayMaxPrice = new HighestValueIndicator(maxPrices, bars_per_day);
        // Getting the min price over the past day
        MinPriceIndicator minPrices = new MinPriceIndicator(series);
        LowestValueIndicator dayMinPrice = new LowestValueIndicator(minPrices, bars_per_day);

        // Going long if the close price goes below the min price
        MultiplierIndicator downDay = new MultiplierIndicator(dayMinPrice, Decimal.valueOf(1 + threshold));
        Rule buyingRule = new UnderIndicatorRule(closePrices, downDay);

        // Going short if the close price goes above the max price
        MultiplierIndicator upDay = new MultiplierIndicator(dayMaxPrice, Decimal.valueOf(1 - threshold));
        Rule sellingRule = new OverIndicatorRule(closePrices, upDay);

        return new BaseStrategy(buyingRule, addStopLossGain(sellingRule, series));
    }
    
}
