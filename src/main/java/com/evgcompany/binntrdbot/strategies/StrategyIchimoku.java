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
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
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
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(tseries, 3);
            IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(tseries, 5);
            IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(tseries, tenkanSen, kijunSen);
            IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(tseries, 9);
            IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(tseries, 5);
            dataset.addSeries(buildChartTimeSeries(tseries, tenkanSen, "tenkanSen"));
            dataset.addSeries(buildChartTimeSeries(tseries, kijunSen, "kijunSen"));
            dataset.addSeries(buildChartTimeSeries(tseries, senkouSpanA, "senkouSpanA"));
            dataset.addSeries(buildChartTimeSeries(tseries, senkouSpanB, "senkouSpanB"));
            dataset.addSeries(buildChartTimeSeries(tseries, chikouSpan, "chikouSpan"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
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
