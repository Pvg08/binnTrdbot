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
    private BigDecimal wavesIncKoef = BigDecimal.valueOf(59);
    private BigDecimal desireableProfitPercent = BigDecimal.valueOf(5);

    private final BigDecimal minPriceChangePercent = BigDecimal.valueOf(0.2);
    private final long maxProcessUpdateIntervalMillis = 10 * 60 * 1000; // 10m
    
    private BigDecimal minPrice = null;
    private BigDecimal lastBaseValue = null;
    
    private SignalItem signalItem = null;
    
    public TradePairWaveProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
        pyramidAutoMaxSize = Integer.MAX_VALUE;
        longModeAuto = true;
        forceMarketOrders = true;
        tradingBalanceQuotePercent = BigDecimal.valueOf(0);
        tradingBalanceMainValue = BigDecimal.valueOf(0.0007);
        stopAfterFinish = true;
    }

    @Override
    protected void checkStatus() {
        super.checkStatus();
        if (inAPIOrder) return;
        if (minPrice == null || orderBaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            minPrice = currentPrice;
            System.out.println("First wave order...");
            doEnter(currentPrice);
            return;
        }
        BigDecimal checkPrice = currentPrice;
        
        DepthCacheProcess process = info.getDepthProcessForPair(symbol);
        if (!process.isStopped() && process.getMillisFromLastUpdate() < maxProcessUpdateIntervalMillis) {
            Map.Entry<BigDecimal, BigDecimal> bestAsk = process.getBestAsk();
            if (bestAsk != null) checkPrice = bestAsk.getKey();
        }
        
        if (minPrice.compareTo(checkPrice) > 0) {
            BigDecimal diffPercent = minPrice.subtract(checkPrice).divide(minPrice, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (diffPercent.compareTo(minPriceChangePercent) > 0) {
                if (lastBaseValue == null) {
                    System.out.println("Second wave order...");
                    lastBaseValue = orderBaseAmount.multiply(wavesSecondaryPercent).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
                } else {
                    System.out.println("Next wave order...");
                    lastBaseValue = lastBaseValue.add(orderAvgPrice.subtract(checkPrice).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP).multiply(wavesIncKoef).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP));
                }
                lastBaseValue = filter.normalizeQuantity(lastBaseValue, false);
                tradingBalanceMainValue = info.convertSumm(baseAssetSymbol, lastBaseValue);
                minPrice = checkPrice;
                doEnter(checkPrice);
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
        } else if (checkPrice.compareTo(orderAvgPrice.multiply(desireableProfitPercent.add(BigDecimal.valueOf(100))).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP)) > 0) {
            System.out.println("Exit from wave order...");
            doExit(checkPrice, true);
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
     * @return the desireableProfitPercent
     */
    public BigDecimal getDesireableProfitPercent() {
        return desireableProfitPercent;
    }

    /**
     * @param desireableProfitPercent the desireableProfitPercent to set
     */
    public void setDesireableProfitPercent(BigDecimal desireableProfitPercent) {
        this.desireableProfitPercent = desireableProfitPercent;
    }
}
