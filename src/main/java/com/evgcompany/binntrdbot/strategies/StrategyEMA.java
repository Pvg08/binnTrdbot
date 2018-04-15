/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.strategies.core.*;
import com.evgcompany.binntrdbot.analysis.*;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyEMA extends StrategyItem {

    public StrategyEMA(StrategiesController controller) {
        super(controller);
        StrategyName = "EMA";
        config.Add("EMA-TimeFrame", new StrategyConfigItem("2", "40", "1", "25"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        int timeFrame = config.GetIntValue("EMA-TimeFrame");
        initializer = (tseries, trecord, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            EMAIndicator ema = new EMAIndicator(closePrice, timeFrame);
            dataset.addSeries(buildChartTimeSeries(tseries, ema, "EMA " + timeFrame));
        };
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(closePrice, timeFrame);
        return new BaseStrategy(
            new OverIndicatorRule(ema, closePrice),
            addStopLossGain(new UnderIndicatorRule(ema, closePrice), series)
        );
    }
}
