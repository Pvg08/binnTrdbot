/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.strategies.core.*;
import com.evgcompany.binntrdbot.analysis.*;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyIchimoku extends StrategyItem {

    int mode = 1;
    
    public StrategyIchimoku(StrategiesController controller, int mode) {
        super(controller);
        StrategyName = "Ichimoku";
        if (mode == 0) mode = 1;
        if (mode > 3) mode = 3;
        if (mode > 1) {
            StrategyName += mode;
        }
        this.mode = mode;
        config.Add("Ichimoku-tenkanSen-TimeFrame", new StrategyConfigItem("2", "20", "1", "3")).setActive(mode == 1);
        config.Add("Ichimoku-kijunSen-TimeFrame", new StrategyConfigItem("2", "20", "1", "5")).setActive(mode == 1 || mode == 2);
        config.Add("Ichimoku-senkouSpan-TimeFrame", new StrategyConfigItem("2", "20", "1", "9")).setActive(mode == 3);
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int tenkanSen_tf = config.GetIntValue("Ichimoku-tenkanSen-TimeFrame");
        int kijunSen_tf = config.GetIntValue("Ichimoku-kijunSen-TimeFrame");
        int senkouSpan_tf = config.GetIntValue("Ichimoku-senkouSpan-TimeFrame");
        
        initializer = (tseries, trecord, dataset) -> {
            IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(tseries, tenkanSen_tf);
            IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(tseries, kijunSen_tf);
            IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(tseries, tenkanSen, kijunSen);
            IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(tseries, senkouSpan_tf);
            //IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(tseries, 5);
            dataset.addSeries(buildChartTimeSeries(tseries, tenkanSen, "tenkanSen " + tenkanSen_tf));
            dataset.addSeries(buildChartTimeSeries(tseries, kijunSen, "kijunSen " + kijunSen_tf));
            dataset.addSeries(buildChartTimeSeries(tseries, senkouSpanA, "senkouSpanA"));
            dataset.addSeries(buildChartTimeSeries(tseries, senkouSpanB, "senkouSpanB " + senkouSpan_tf));
            //dataset.addSeries(buildChartTimeSeries(tseries, chikouSpan, "chikouSpan"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, tenkanSen_tf);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, kijunSen_tf);
        //IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, senkouSpan_tf);
        //IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        Rule entryRule;
        Rule exitRule;

        switch (mode) {
            case 1:
                entryRule = new CrossedDownIndicatorRule(kijunSen, tenkanSen);
                exitRule = new CrossedUpIndicatorRule(kijunSen, tenkanSen);
                break;
            case 2:
                entryRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
                exitRule = new CrossedUpIndicatorRule(closePrice, kijunSen);
                break;
            case 3:
                entryRule = new CrossedDownIndicatorRule(closePrice, senkouSpanB);
                exitRule = new CrossedUpIndicatorRule(closePrice, senkouSpanB);
                break;
            default:
                exitRule = entryRule = new BooleanRule(false);
        }
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
