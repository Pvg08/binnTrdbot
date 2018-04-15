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
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategySMA extends StrategyItem {

    public StrategySMA(StrategiesController controller) {
        super(controller);
        StrategyName = "SMA";
        config.Add("SMA-TimeFrame", new StrategyConfigItem("2", "30", "1", "22"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        int timeFrame = config.GetIntValue("SMA-TimeFrame");
        initializer = (tseries, trecord, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            SMAIndicator sma = new SMAIndicator(closePrice, timeFrame);
            dataset.addSeries(buildChartTimeSeries(tseries, sma, "SMA " + timeFrame));
        };
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, timeFrame);
        return new BaseStrategy(
            new OverIndicatorRule(sma, closePrice),
            addStopLossGain(new UnderIndicatorRule(sma, closePrice), series)
        );
    }
}
