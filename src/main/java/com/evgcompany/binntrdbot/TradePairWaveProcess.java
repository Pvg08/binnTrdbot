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
import com.evgcompany.binntrdbot.strategies.core.OrderActionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 *
 * @author EVG_Adminer
 */
public class TradePairWaveProcess extends TradePairStrategyProcess implements TradeSignalProcessInterface {
    
    private BigDecimal wavesSecondaryPercent = BigDecimal.valueOf(25);
    private BigDecimal wavesIncKoef = BigDecimal.valueOf(65);

    private double initialProfitPercent = 5;
    private double minProfitPercent = 0.5;
    private double halfDivideProfitOrdersCount = 10;

    private final double minPriceChangeInitialPercent = 0.15;
    
    private BigDecimal minPrice = null;
    private BigDecimal lastBaseValue = null;
    
    private SignalItem signalItem = null;
    
    public TradePairWaveProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
        pyramidAutoMaxSize = Integer.MAX_VALUE;
        longModeAuto = true;
        forceMarketOrders = true;
        stopAfterFinish = true;
    }
    
    @Override
    protected void runStart() {
        super.setMainStrategy("EMA");
        super.runStart();
        strategiesController.getMainStrategyItem().getConfig().setParam("EMA-TimeFrame", BigDecimal.valueOf(12));
    }
    
    @Override
    protected void checkStatus() {
        info.setLatestPrice(symbol, currentPrice);
        ordersController.updatePairTradeText(orderCID);
        if (minPrice == null || orderBaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (doEnter(currentPrice, true)) {
                minPrice = currentPrice;
            }
            return;
        }
        BigDecimal checkPrice = depthProcess.getBestAskOrDefault(currentPrice);
        
        if (minPrice.compareTo(checkPrice) > 0) {
            BigDecimal diffPercent = minPrice.subtract(checkPrice).divide(minPrice, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            System.out.println(symbol + " diffPercent(min,check) = " + diffPercent);
            if (diffPercent.compareTo(BigDecimal.valueOf(minPriceChangeInitialPercent)) > 0) {
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
                } else {
                    lastBaseValue = tmpLastBase;
                }
            }
            return;
        }
        
        checkPrice = depthProcess.getBestBidOrDefault(currentPrice);
        
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
            BigDecimal aimPrice = orderAvgPrice.multiply(BigDecimal.valueOf(checkProfitPercent).add(BigDecimal.valueOf(100))).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
            System.out.println(symbol + " Aim price = " + aimPrice);
            if (checkPrice.compareTo(aimPrice) > 0) {
                OrderActionType saction = OrderActionType.DO_STRATEGY_EXIT;
                if (
                        !strategiesController.getMainStrategy().equals("No Strategy") && 
                        !strategiesController.getMainStrategy().equals("Neural Network") && 
                        !strategiesController.getMainStrategy().equals("Auto")
                ) {
                    System.out.println(symbol + " Checking strategy for exit...");
                    saction = strategiesController.checkStatus(true, false, null);
                }
                if (saction == OrderActionType.DO_STRATEGY_EXIT) {
                    System.out.println(symbol + " Exit from wave order...");
                    doExit(checkPrice, true);
                }
            }
        }
    }
    
    @Override
    public void setMainStrategy(String mainStrategy) {
        // nothing
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
