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
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyWIP extends StrategyItem {

    public StrategyWIP(StrategiesController controller) {
        super(controller);
        StrategyName = "WIP";
    }

    /*
    
    Maybe good strategy:
    
    strategy("Ichimoku + Daily-Candle_X + HULL-MA_X + MacD", shorttitle="", overlay=true, default_qty_type=strategy.percent_of_equity, max_bars_back=720, default_qty_value=100, calc_on_order_fills= true, calc_on_every_tick=true, pyramiding=0)

    keh=input(title="Double HullMA",type=integer,defval=14, minval=1)
    dt = input(defval=0.0010, title="Decision Threshold (0.001)", type=float, step=0.0001)
    SL = input(defval=-500.00, title="Stop Loss in $", type=float, step=1)
    TP = input(defval=25000.00, title="Target Point in $", type=float, step=1)

    ot=1
    n2ma=2*wma(close,round(keh/2))
    nma=wma(close,keh)
    diff=n2ma-nma
    sqn=round(sqrt(keh))
    n2ma1=2*wma(close[1],round(keh/2))
    nma1=wma(close[1],keh)
    diff1=n2ma1-nma1
    sqn1=round(sqrt(keh))
    n1=wma(diff,sqn)
    n2=wma(diff1,sqn)
    b=n1>n2?lime:red
    c=n1>n2?green:red
    d=n1>n2?red:green
    confidence=(security(tickerid, 'D', close)-security(tickerid, 'D', close[1]))/security(tickerid, 'D', close[1])
    conversionPeriods = input(9, minval=1, title="Conversion Line Periods")
    basePeriods = input(26, minval=1, title="Base Line Periods")
    laggingSpan2Periods = input(52, minval=1, title="Lagging Span 2 Periods")
    displacement = input(26, minval=1, title="Displacement")
    donchian(len) => avg(lowest(len), highest(len))
    conversionLine = donchian(conversionPeriods)
    baseLine = donchian(basePeriods)
    leadLine1 = avg(conversionLine, baseLine)
    leadLine2 = donchian(laggingSpan2Periods)
    LS=close, offset = -displacement
    MACD_Length = input(9)
    MACD_fastLength = input(12)
    MACD_slowLength = input(26)
    MACD = ema(close, MACD_fastLength) - ema(close, MACD_slowLength)
    aMACD = ema(MACD, MACD_Length)

    closelong = n1<n2 and close<n2 and confidence<dt or strategy.openprofit<SL or strategy.openprofit>TP
    if (closelong)
        strategy.close("Long")

    closeshort = n1>n2 and close>n2 and confidence>dt or strategy.openprofit<SL or strategy.openprofit>TP
    if (closeshort)
        strategy.close("Short")

    longCondition = n1>n2 and strategy.opentrades<ot and confidence>dt and close>n2 and leadLine1>leadLine2 and open<LS and MACD>aMACD
    if (longCondition)
        strategy.entry("Long",strategy.long)

    shortCondition = n1<n2 and strategy.opentrades<ot and confidence<dt and close<n2 and leadLine1<leadLine2 and open>LS and MACD<aMACD
    if (shortCondition)
        strategy.entry("Short",strategy.short)

    */
    
    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, trecord, dataset) -> {

        };

        // http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf
        // FisherIndicator fisher = new FisherIndicator(series);

        // https://alanhull.com/hull-moving-average
        // HMAIndicator hma = new HMAIndicator(new ClosePriceIndicator(series), 9);
        
        // http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon
        // AroonUpIndicator arronUp = new AroonUpIndicator(series, 5);

        //OpenPriceIndicator openPriceIndicator = new OpenPriceIndicator(series);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        EMAIndicator shortEma = new EMAIndicator(closePrice, 5);
        EMAIndicator longEma = new EMAIndicator(closePrice, 20);

        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        SMAIndicator sma = new SMAIndicator(closePrice, 12);
        MACDIndicator macd = new MACDIndicator(closePrice, 9, 15);

        Rule entryRule = new CrossedDownIndicatorRule(closePrice, kijunSen)
                //new OverIndicatorRule(shortEma, longEma)
                //.and(new OverIndicatorRule(shortEma, closePrice))
                //.and(new OverIndicatorRule(sma, closePrice))
                //.and(new IsRisingRule(macd, 2))
                .and(new UnderIndicatorRule(rsi, new ConstantIndicator(Decimal.valueOf(90))))
                ;

        Rule exitRule = new CrossedUpIndicatorRule(closePrice, kijunSen)
                //new UnderIndicatorRule(shortEma, longEma)
                //.and(new UnderIndicatorRule(shortEma, closePrice))
                //.and(new UnderIndicatorRule(sma, closePrice))
                //.and(new IsFallingRule(macd, 2))
                .and(new OverIndicatorRule(rsi, new ConstantIndicator(Decimal.valueOf(10))))
                ;

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
