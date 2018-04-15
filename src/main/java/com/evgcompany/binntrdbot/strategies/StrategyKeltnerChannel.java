/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.strategies.core.*;
import com.evgcompany.binntrdbot.analysis.*;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelLowerIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelMiddleIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelUpperIndicator;
import org.ta4j.core.trading.rules.IsFallingRule;
import org.ta4j.core.trading.rules.IsRisingRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyKeltnerChannel extends StrategyItem {

    public StrategyKeltnerChannel(StrategiesController controller) {
        super(controller);
        StrategyName = "Keltner Channel";
        config.Add("Keltner-TimeFrame", new StrategyConfigItem("2", "40", "2", "20"));
        config.Add("Keltner-Ratio", new StrategyConfigItem("1", "3", "0.25", "1.5"));
        config.Add("MACD-TimeFrameBase", new StrategyConfigItem("3", "21", "2", "9"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int macd_from_tf = config.GetIntValue("MACD-TimeFrameBase");
        int macd_to_tf = (int) Math.round(macd_from_tf * 5.0 / 3.0);
        int timeframe = config.GetIntValue("Keltner-TimeFrame");
        Decimal ratio = config.GetNumValue("Keltner-Ratio");
        
        initializer = (tseries, trecord, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            KeltnerChannelMiddleIndicator keltnerM = new KeltnerChannelMiddleIndicator(closePrice, timeframe);
            KeltnerChannelLowerIndicator keltnerL = new KeltnerChannelLowerIndicator(keltnerM, ratio, timeframe);
            KeltnerChannelUpperIndicator keltnerU = new KeltnerChannelUpperIndicator(keltnerM, ratio, timeframe);
            dataset.addSeries(buildChartTimeSeries(tseries, keltnerM, "Keltner Middle"));
            dataset.addSeries(buildChartTimeSeries(tseries, keltnerL, "Keltner Lower"));
            dataset.addSeries(buildChartTimeSeries(tseries, keltnerU, "Keltner Upper"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, macd_from_tf, macd_to_tf);
        KeltnerChannelMiddleIndicator keltnerM = new KeltnerChannelMiddleIndicator(closePrice, timeframe);
        KeltnerChannelLowerIndicator keltnerL = new KeltnerChannelLowerIndicator(keltnerM, ratio, timeframe);
        KeltnerChannelUpperIndicator keltnerU = new KeltnerChannelUpperIndicator(keltnerM, ratio, timeframe);
        
        Rule entryRule = new UnderIndicatorRule(keltnerL, closePrice)
                .and(new UnderIndicatorRule(new PreviousValueIndicator(keltnerL, 1), new PreviousValueIndicator(closePrice, 1)))
                .and(new OverIndicatorRule(keltnerM, closePrice))
                .and(new IsRisingRule(macd, 2));
        
        Rule exitRule = new OverIndicatorRule(keltnerU, closePrice)
                .and(new OverIndicatorRule(new PreviousValueIndicator(keltnerU, 1), new PreviousValueIndicator(closePrice, 1)))
                .and(new UnderIndicatorRule(keltnerM, closePrice))
                .and(new IsFallingRule(macd, 2));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
