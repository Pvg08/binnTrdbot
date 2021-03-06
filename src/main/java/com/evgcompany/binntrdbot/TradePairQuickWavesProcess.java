/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.ta4j.core.Decimal;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

/**
 *
 * @author EVG_Adminer
 */
public class TradePairQuickWavesProcess extends TradePairStrategyProcess {
    
    private double minProfitPercent = 0.25;
    private double baseProfitPercent = 0.85;
    private long maxWaitTimeBuySeconds = 2400;
    private long maxWaitTimeSellSeconds = 0;
    private int emaDistance = 14;
    private int emaDistanceLong = 28;
    
    private BigDecimal enterPrice = null;
    private BigDecimal exitPrice = null;
    
    private int restartTickCounter = 0;
    private int restartTickMaxCounter = 2;
    private long lastOrderTime = 0;
    
    public TradePairQuickWavesProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
        pyramidAutoMaxSize = Integer.MAX_VALUE;
        longModeAuto = true;
        forceMarketOrders = false;
        stopAfterFinish = false;
        strategiesController.setCanAutoPickParams(false);
    }
    
    @Override
    protected void runStart() {
        super.setMainStrategy("Bollinger3");
        useBuyStopLimited = false;
        useSellStopLimited = false;
        super.runStart();
        strategiesController.getMainStrategyItem().getConfig().setParam("Bollinger-Length", BigDecimal.valueOf(emaDistance));
        strategiesController.updateStrategies();
    }
    
    @Override
    protected void checkStatus() {
        
        info.setLatestPrice(symbol, currentPrice);
        ordersController.updatePairTradeText(orderCID);
        
        if (enterPrice != null) {
            if (exitPrice == null && pyramidSize == 0 && (orderBaseAmount == null || orderBaseAmount.compareTo(BigDecimal.ZERO) <= 0)) {
                enterPrice = null;
                return;
            }
            if (exitPrice != null && pyramidSize > 0) {
                return;
            }
            if (exitPrice != null && pyramidSize == 0) {
                if (restartTickCounter >= restartTickMaxCounter) {
                    restartTickCounter = 0;
                    enterPrice = null;
                    exitPrice = null;
                } else {
                    restartTickCounter++;
                }
                return;
            }
        }
        
        if (enterPrice == null && exitPrice == null) {
            int endIndex = series.getEndIndex();
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            EMAIndicator ema = new EMAIndicator(closePrice, emaDistance);
            EMAIndicator ema2 = new EMAIndicator(closePrice, emaDistanceLong);
            double ema_k = ((ema.getValue(endIndex).doubleValue() / ema.getValue(endIndex-1).doubleValue()) + 
                    (ema.getValue(endIndex).doubleValue() / ema.getValue(endIndex-2).doubleValue()) + 
                    (ema2.getValue(endIndex).doubleValue() / ema2.getValue(endIndex-1).doubleValue())) / 3;
            
            System.out.println("EMA_K["+symbol+"] = " + ema_k);
            
            if (ema_k < 0.97 || ema_k > 1.03) {
                return;
            }
            
            BigDecimal mult = strategiesController.getMainStrategyItem().getConfig().GetValue("Bollinger-Mult");
            StandardDeviationIndicator dev = new StandardDeviationIndicator(closePrice, emaDistance);
            BollingerBandsMiddleIndicator bollingerM = new BollingerBandsMiddleIndicator(ema);
            BollingerBandsLowerIndicator BBB = new BollingerBandsLowerIndicator(bollingerM, dev, Decimal.valueOf(mult));
            BollingerBandsUpperIndicator BBT = new BollingerBandsUpperIndicator(bollingerM, dev, Decimal.valueOf(mult));
        
            currentPrice = depthProcess.getBestAskOrDefault(currentPrice);
            
            Double baRatio = depthProcess.getBidAskRatio(BigDecimal.valueOf(baseProfitPercent * 8), currentPrice);
            if (baRatio != null) {
                System.out.println("baRatio["+symbol+"] = " + baRatio);
                if (baRatio < 0.95) {
                    return;
                }
            } else {
                System.out.println("baRatio["+symbol+"] = NA");
                return;
            }
            
            double ema_price = ema.getValue(series.getEndIndex()).doubleValue();
            double cur_price = currentPrice.doubleValue();
            double down_price = ema_price*(100-baseProfitPercent/2)*0.01;
            
            if (cur_price < down_price) {
                enterPrice = currentPrice;
            } else {
                enterPrice = BigDecimal.valueOf(down_price);
            }
            
            if (enterPrice.compareTo(BigDecimal.valueOf(BBB.getValue(endIndex).doubleValue())) <= 0) {
                enterPrice = BigDecimal.valueOf(0.04 * (BBB.getValue(endIndex).doubleValue() * 24 + BBT.getValue(endIndex).doubleValue()));
            }
            
            enterPrice = filter.normalizePrice(enterPrice);
            if (filter.isFilterPrice() && enterPrice.compareTo(currentPrice) >= 0) {
                enterPrice = enterPrice.subtract(filter.getFilterPriceTickSize());
            }
            
            doEnter(enterPrice, true);
            lastOrderTime = System.currentTimeMillis();
            return;
        }
        
        if (enterPrice != null && exitPrice == null && pyramidSize > 0) {
            int endIndex = series.getEndIndex();
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            EMAIndicator ema = new EMAIndicator(closePrice, emaDistance);
            BigDecimal mult = strategiesController.getMainStrategyItem().getConfig().GetValue("Bollinger-Mult");
            StandardDeviationIndicator dev = new StandardDeviationIndicator(closePrice, emaDistance);
            BollingerBandsMiddleIndicator bollingerM = new BollingerBandsMiddleIndicator(ema);
            BollingerBandsLowerIndicator BBB = new BollingerBandsLowerIndicator(bollingerM, dev, Decimal.valueOf(mult));
            BollingerBandsUpperIndicator BBT = new BollingerBandsUpperIndicator(bollingerM, dev, Decimal.valueOf(mult));

            currentPrice = depthProcess.getBestBidOrDefault(currentPrice);
            
            double buy_price = orderAvgPrice.doubleValue();
            double cur_price = currentPrice.doubleValue();
            double up_price = buy_price*(100+baseProfitPercent)*0.01;
            if (up_price > cur_price) {
                exitPrice = BigDecimal.valueOf(up_price);
            } else {
                exitPrice = currentPrice;
            }
            
            if (exitPrice.compareTo(BigDecimal.valueOf(BBT.getValue(endIndex).doubleValue())) >= 0) {
                exitPrice = BigDecimal.valueOf(0.04 * (BBB.getValue(endIndex).doubleValue() + BBT.getValue(endIndex).doubleValue() * 24));
            }
            if (exitPrice.
                    subtract(enterPrice).
                    divide(enterPrice, RoundingMode.HALF_UP).
                    multiply(BigDecimal.valueOf(100)).
                    compareTo(BigDecimal.valueOf(minProfitPercent)) < 0
            ) {
                up_price = buy_price*(100+minProfitPercent)*0.01;
                exitPrice = BigDecimal.valueOf(up_price);
            }
            
            exitPrice = filter.normalizePrice(exitPrice);
            if (filter.isFilterPrice() && exitPrice.compareTo(enterPrice) <= 0) {
                exitPrice = exitPrice.add(filter.getFilterPriceTickSize());
            }
            
            doExit(exitPrice, true);
            lastOrderTime = System.currentTimeMillis();
            return;
        }
    }

    @Override
    protected void runBody() {
        if (!inAPIOrder && !need_stop) {
            checkStatus();
        }
        if (
                (enterPrice != null || exitPrice != null || pyramidSize > 0) && 
                lastOrderTime > 0 && 
                (
                    (maxWaitTimeBuySeconds > 0 && (exitPrice == null || pyramidSize <= 0) && (System.currentTimeMillis() - lastOrderTime) > maxWaitTimeBuySeconds*1000) ||
                    (maxWaitTimeSellSeconds > 0 && (exitPrice != null && pyramidSize  > 0) && (System.currentTimeMillis() - lastOrderTime) > maxWaitTimeSellSeconds*1000)
                ) 
            )
        {
            System.out.println("TIMEOUT FOR QUICK WAVE!");
            lastOrderTime = System.currentTimeMillis();
            orderCancel();
            if (exitPrice != null && pyramidSize > 0) {
                doWait(1000);
                forceMarketOrders = true;
                doExit(exitPrice, true);
                forceMarketOrders = false;
                doWait(10000);
            }
            restartTickCounter = 0;
            enterPrice = null;
            exitPrice = null;
        }
    }
    
    @Override
    public void setMainStrategy(String mainStrategy) {
        // nothing
    }
}
