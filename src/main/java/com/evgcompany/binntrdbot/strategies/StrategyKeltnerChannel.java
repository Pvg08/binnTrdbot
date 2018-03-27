/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
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
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            MACDIndicator macd = new MACDIndicator(closePrice, 9, 15);
            KeltnerChannelMiddleIndicator keltnerM = new KeltnerChannelMiddleIndicator(closePrice, 20);
            KeltnerChannelLowerIndicator keltnerL = new KeltnerChannelLowerIndicator(keltnerM, Decimal.valueOf(1.5), 20);
            KeltnerChannelUpperIndicator keltnerU = new KeltnerChannelUpperIndicator(keltnerM, Decimal.valueOf(1.5), 20);
            dataset.addSeries(buildChartTimeSeries(tseries, keltnerM, "Keltner Middle"));
            dataset.addSeries(buildChartTimeSeries(tseries, keltnerL, "Keltner Lower"));
            dataset.addSeries(buildChartTimeSeries(tseries, keltnerU, "Keltner Upper"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, 9, 15);
        KeltnerChannelMiddleIndicator keltnerM = new KeltnerChannelMiddleIndicator(closePrice, 20);
        KeltnerChannelLowerIndicator keltnerL = new KeltnerChannelLowerIndicator(keltnerM, Decimal.valueOf(1.5), 20);
        KeltnerChannelUpperIndicator keltnerU = new KeltnerChannelUpperIndicator(keltnerM, Decimal.valueOf(1.5), 20);
        
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
