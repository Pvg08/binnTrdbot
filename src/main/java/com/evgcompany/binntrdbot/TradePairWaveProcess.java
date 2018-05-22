/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.DepthCacheProcess;
import com.evgcompany.binntrdbot.signal.SignalItem;
import com.evgcompany.binntrdbot.signal.TradeSignalProcessInterface;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 *
 * @author EVG_Adminer
 */
public class TradePairWaveProcess extends AbstractTradePairProcess implements TradeSignalProcessInterface {
    
    private BigDecimal wavesSecondaryPercent = BigDecimal.valueOf(25);
    private BigDecimal wavesIncKoef = BigDecimal.valueOf(65);

    private double initialProfitPercent = 8;
    private double minProfitPercent = 0.75;
    private double halfDivideProfitOrdersCount = 10;
    private final double minPriceChangeInitialPercent = 0.15;
    
    private BigDecimal minPrice = null;
    private BigDecimal lastBaseValue = null;
    private BigDecimal secondBaseValue = null;
    
    private SignalItem signalItem = null;
    
    public TradePairWaveProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
        pyramidAutoMaxSize = Integer.MAX_VALUE;
        longModeAuto = true;
        forceMarketOrders = true;
        tradingBalanceQuotePercent = BigDecimal.valueOf(0);
        tradingBalanceMainValue = BigDecimal.valueOf(0.0005);
        stopAfterFinish = true;
    }

    @Override
    protected void checkStatus() {
        super.checkStatus();
        if (inAPIOrder) return;
        if (minPrice == null || orderBaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (doEnter(currentPrice, true)) {
                minPrice = currentPrice;
            }
            return;
        }
        BigDecimal checkPrice = currentPrice;
        
        DepthCacheProcess process = info.getDepthProcessForPair(symbol);
        if (!process.isStopped() && process.getMillisFromLastUpdate() < maxProcessUpdateIntervalMillis) {
            Map.Entry<BigDecimal, BigDecimal> bestAsk = process.getBestAsk();
            if (bestAsk != null) checkPrice = bestAsk.getKey();
        }
        
        if (minPrice.compareTo(checkPrice) > 0) {
            BigDecimal koef = BigDecimal.ONE;
            if (lastBaseValue != null && secondBaseValue != null) {
                koef = lastBaseValue.divide(secondBaseValue, RoundingMode.HALF_UP);
                koef = BigDecimal.valueOf(Math.sqrt(koef.doubleValue()));
            }
            BigDecimal diffPercent = minPrice.subtract(checkPrice).divide(minPrice, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (diffPercent.compareTo(koef.multiply(BigDecimal.valueOf(minPriceChangeInitialPercent))) > 0) {
                BigDecimal tmpLastBase = lastBaseValue;
                if (lastBaseValue == null) {
                    lastBaseValue = orderBaseAmount.multiply(wavesSecondaryPercent).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
                } else {
                    BigDecimal modKoef = wavesIncKoef;
                    if (filter.isFilterQty() && filter.getFilterQtyStep() != null) {
                        modKoef = modKoef.multiply(filter.getFilterQtyStep());
                    }
                    BigDecimal priceOffset = orderAvgPrice.subtract(checkPrice).divide(orderAvgPrice, RoundingMode.HALF_UP);
                    lastBaseValue = lastBaseValue.add(priceOffset.multiply(modKoef));
                }
                lastBaseValue = filter.normalizeQuantity(lastBaseValue, false);
                tradingBalanceMainValue = info.convertSumm(baseAssetSymbol, lastBaseValue);
                if (doEnter(checkPrice, true)) {
                    minPrice = checkPrice;
                    if (secondBaseValue == null) secondBaseValue = lastBaseValue;
                } else {
                    lastBaseValue = tmpLastBase;
                }
            }
            return;
        }
        
        checkPrice = currentPrice;
        if (!process.isStopped() && process.getMillisFromLastUpdate() < maxProcessUpdateIntervalMillis) {
            Map.Entry<BigDecimal, BigDecimal> bestBid = process.getBestBid();
            if (bestBid != null) checkPrice = bestBid.getKey();
        }
        
        if (signalItem != null && checkPrice.compareTo(orderAvgPrice) > 0 && signalItem.isTargetReachedAtPrice(checkPrice)) {
            System.out.println("Exit from wave signal order...");
            doExit(checkPrice, true);
        } else {
            double checkProfitPercent = initialProfitPercent;
            if (Math.abs(pyramidSize) > 0) {
                checkProfitPercent = checkProfitPercent/Math.pow(2.0, Math.abs(pyramidSize - 0.99999)/halfDivideProfitOrdersCount);
                if (checkProfitPercent < minProfitPercent) {
                    checkProfitPercent = minProfitPercent;
                }
            }
            System.out.println(symbol + " Check profit percent = " + checkProfitPercent);
            if (checkPrice.compareTo(orderAvgPrice.multiply(BigDecimal.valueOf(checkProfitPercent).add(BigDecimal.valueOf(100))).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP)) > 0) {
                System.out.println("Exit from wave order...");
                doExit(checkPrice, true);
            }
        }
    }
    
    @Override
    public SignalItem getSignalItem() {
        return signalItem;
    }

    @Override
    public void setSignalItem(SignalItem item) {
        signalItem = item;
    }
    
    /**
     * @return the wavesSecondaryPercent
     */
    public BigDecimal getWavesSecondaryPercent() {
        return wavesSecondaryPercent;
    }

    /**
     * @param wavesSecondaryPercent the wavesSecondaryPercent to set
     */
    public void setWavesSecondaryPercent(BigDecimal wavesSecondaryPercent) {
        this.wavesSecondaryPercent = wavesSecondaryPercent;
    }

    /**
     * @return the wavesIncKoef
     */
    public BigDecimal getWavesIncKoef() {
        return wavesIncKoef;
    }

    /**
     * @param wavesIncKoef the wavesIncKoef to set
     */
    public void setWavesIncKoef(BigDecimal wavesIncKoef) {
        this.wavesIncKoef = wavesIncKoef;
    }

    /**
     * @return the minProfitPercent
     */
    public double getMinProfitPercent() {
        return minProfitPercent;
    }

    /**
     * @param minProfitPercent the minProfitPercent to set
     */
    public void setMinProfitPercent(double minProfitPercent) {
        this.minProfitPercent = minProfitPercent;
    }

    /**
     * @return the halfDivideProfitOrdersCount
     */
    public double getHalfDivideProfitOrdersCount() {
        return halfDivideProfitOrdersCount;
    }

    /**
     * @param halfDivideProfitOrdersCount the halfDivideProfitOrdersCount to set
     */
    public void setHalfDivideProfitOrdersCount(double halfDivideProfitOrdersCount) {
        this.halfDivideProfitOrdersCount = halfDivideProfitOrdersCount;
    }

    /**
     * @return the initialProfitPercent
     */
    public double getInitialProfitPercent() {
        return initialProfitPercent;
    }

    /**
     * @param initialProfitPercent the initialProfitPercent to set
     */
    public void setInitialProfitPercent(double initialProfitPercent) {
        this.initialProfitPercent = initialProfitPercent;
    }
}
