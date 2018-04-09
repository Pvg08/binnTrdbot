/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.StrategyConfigItem;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyMovingMomentum extends StrategyItem {

    public StrategyMovingMomentum(StrategiesController controller) {
        super(controller);
        StrategyName = "MovingMomentum";
        config.Add("EMA1-TimeFrame", new StrategyConfigItem("2", "20", "1", "5"));
        config.Add("EMA2-TimeFrame", new StrategyConfigItem("21", "76", "5", "26"));
        config.Add("EMACD-TimeFrame", new StrategyConfigItem("8", "50", "2", "18"));
        config.Add("STOK-TimeFrame", new StrategyConfigItem("3", "25", "1", "14"));
        config.Add("MACD-Threshold", new StrategyConfigItem("1", "40", "1", "20"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int ema1_tf = config.GetIntValue("EMA1-TimeFrame");
        int ema2_tf = config.GetIntValue("EMA2-TimeFrame");
        int stok_tf = config.GetIntValue("STOK-TimeFrame");
        int emacd_tf = config.GetIntValue("EMACD-TimeFrame");
        int macd_th = config.GetIntValue("MACD-Threshold");
        
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            EMAIndicator shortEma = new EMAIndicator(closePrice, ema1_tf);
            EMAIndicator longEma = new EMAIndicator(closePrice, ema2_tf);
            dataset.addSeries(buildChartTimeSeries(tseries, shortEma, "EMA " + ema1_tf));
            dataset.addSeries(buildChartTimeSeries(tseries, longEma, "EMA " + ema2_tf));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        EMAIndicator shortEma = new EMAIndicator(closePrice, ema1_tf);
        EMAIndicator longEma = new EMAIndicator(closePrice, ema2_tf);

        StochasticOscillatorKIndicator stoK = new StochasticOscillatorKIndicator(series, stok_tf);

        MACDIndicator macd = new MACDIndicator(closePrice, ema1_tf, ema2_tf);
        EMAIndicator emaMacd = new EMAIndicator(macd, emacd_tf);
        
        Rule entryRule = new OverIndicatorRule(shortEma, longEma)
                .and(new CrossedDownIndicatorRule(stoK, Decimal.valueOf(macd_th)))
                .and(new OverIndicatorRule(macd, emaMacd));
        
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma)
                .and(new CrossedUpIndicatorRule(stoK, Decimal.valueOf(100 - macd_th)))
                .and(new UnderIndicatorRule(macd, emaMacd));

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
