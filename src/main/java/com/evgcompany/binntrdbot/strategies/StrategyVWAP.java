/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.volume.MVWAPIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyVWAP extends StrategyItem {

    public StrategyVWAP(StrategiesController controller) {
        super(controller);
        StrategyName = "VWAP";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            OpenPriceIndicator openPrice = new OpenPriceIndicator(tseries);
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            VWAPIndicator vwap = new VWAPIndicator(tseries, 10);
            MVWAPIndicator mvwap = new MVWAPIndicator(vwap, 3);
            dataset.addSeries(buildChartTimeSeries(tseries, mvwap, "MVWAP 3 10"));
        };
        
        OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VWAPIndicator vwap = new VWAPIndicator(series, 10);
        MVWAPIndicator mvwap = new MVWAPIndicator(vwap, 3);

        Rule entryRule = new CrossedUpIndicatorRule(mvwap, closePrice)
                .and(new OverIndicatorRule(closePrice, openPrice));
        
        Rule exitRule = new CrossedDownIndicatorRule(mvwap, closePrice)
                .and(new UnderIndicatorRule(closePrice, openPrice));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
