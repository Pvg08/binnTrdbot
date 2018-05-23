/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author EVG_adm_T
 */
public class TradePairProcess extends TradePairSignalProcess {
    
    protected boolean isTryingToSellOnPeak = false;
    protected boolean isTryingToBuyOnDip = false;
    protected boolean buyOnStart = false;
    protected boolean sellOpenOrdersOnPeak = false;
    
    public TradePairProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
    }
    
    @Override
    protected void runStart() {
        super.runStart();
        if (isTryingToSellOnPeak) {
            initSellAllOnPeak();
        } else if (isTryingToBuyOnDip) {
            initBuyOnDip();
        }
        
        if (buyOnStart && canBuyForCoinRating() && !strategiesController.isShouldLeave()) {
            doBuy();
        }

        app.log(symbol + " initialized. Current price = " + NumberFormatter.df8.format(currentPrice));
    }
    
    private void initBuyOnDip() {
        isTryingToSellOnPeak = false;
        stopAfterFinish = true;
        longModeAuto = true;
    }
    
    private void initSellAllOnPeak() {
        isTryingToBuyOnDip = false;
        List<Trade> trades = client.getAllTrades(symbol);
        if (trades != null && !trades.isEmpty()) {
            Collections.sort(
                trades, 
                (Trade lhs, Trade rhs) -> (
                    lhs.getTime() > rhs.getTime() ? -1 : (lhs.getTime() < rhs.getTime()) ? 1 : 0
                )
            );
            BigDecimal avgTradeBuyPrice = BigDecimal.ZERO;

            CoinBalanceItem qbalance = BalanceController.getInstance().getCoinBalanceInfo(baseAssetSymbol);
            BigDecimal free_cnt = qbalance.getFreeValue();
            BigDecimal acc_cnt = BigDecimal.ZERO;
            int ordersCount = 0;
            
            List<Long> ordersToCancel = new ArrayList<>();

            if (sellOpenOrdersOnPeak) {
                List<Order> openOrders = client.getOpenOrders(symbol);
                for (int i=0; i < openOrders.size(); i++) {
                    if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL) {
                        ordersToCancel.add(openOrders.get(i).getOrderId());
                    }
                }
            }
            
            if (!ordersToCancel.isEmpty()) {
                free_cnt = qbalance.getValue();
            }
            for (Trade trade : trades) {
                if (trade.isBuyer() && acc_cnt.compareTo(free_cnt) < 0) {
                    BigDecimal ctprice = new BigDecimal(trade.getPrice());
                    BigDecimal ctvol = new BigDecimal(trade.getQty());
                    if (acc_cnt.add(ctvol).compareTo(free_cnt) > 0) {
                        ctvol = free_cnt.subtract(acc_cnt);
                    }
                    avgTradeBuyPrice = ctprice.multiply(ctvol).add(avgTradeBuyPrice.multiply(acc_cnt)).divide(ctvol.add(acc_cnt), RoundingMode.HALF_UP);
                    ordersCount++;
                    acc_cnt = acc_cnt.add(ctvol);
                }
            }
            
            if (ordersCount == 0) {
                avgTradeBuyPrice = BigDecimal.valueOf(info.getLastPrices().get(symbol));
                ordersCount = 1;
            }

            if (free_cnt.compareTo(BigDecimal.ZERO) > 0 || !ordersToCancel.isEmpty()) {
                if (!ordersToCancel.isEmpty()) {
                    app.log("Canceling orders: " + ordersToCancel, true, true);
                    ordersController.cancelOrdersList(orderCID, ordersToCancel);
                    doWait(10000);
                    qbalance = BalanceController.getInstance().getCoinBalanceInfo(baseAssetSymbol);
                    free_cnt = qbalance.getFreeValue();
                }
                if (free_cnt.compareTo(BigDecimal.ZERO) > 0) {
                    boolean result = ordersController.PreBuySell(orderCID, free_cnt, avgTradeBuyPrice/*, currentLimitSellQty, currentLimitSellPrice*/);
                    if (result) {
                        app.log("START WAITING to sell " + NumberFormatter.df8.format(free_cnt) + " " + baseAssetSymbol + " for price more than " + NumberFormatter.df8.format(avgTradeBuyPrice) + " " + quoteAssetSymbol, true, true);
                        inAPIOrder = false;
                        lastBuyPrice = avgTradeBuyPrice;
                        orderBaseAmount = free_cnt;
                        orderQuoteAmount = free_cnt.multiply(avgTradeBuyPrice);
                        orderAvgPrice = avgTradeBuyPrice;
                        stopAfterFinish = true;
                        longModeAuto = true;
                        pyramidSize = ordersCount;
                        ordersController.setPairOrderPrice(orderCID, orderAvgPrice);
                        ordersController.updatePairTradeText(orderCID);
                        return;
                    } else {
                        app.log("Error in PreBuy method!", true, true);
                    }
                }
            }
        }
        app.log("Can't set SellAllPeak mode for " + symbol, true, true);
    }
    
    /**
     * @return the isTryingToSellOnPeak
     */
    public boolean isTryingToSellOnPeak() {
        return isTryingToSellOnPeak;
    }

    /**
     * @param isTryingToSellOnPeak the isTryingToSellOnPeak to set
     */
    public void setTryingToSellOnPeak(boolean isTryingToSellOnPeak) {
        this.isTryingToSellOnPeak = isTryingToSellOnPeak;
    }

    /**
     * @return the isTryingToBuyDip
     */
    public boolean isTryingToBuyDip() {
        return isTryingToBuyOnDip;
    }

    /**
     * @param isTryingToBuyDip the isTryingToBuyDip to set
     */
    public void setTryingToBuyDip(boolean isTryingToBuyDip) {
        this.isTryingToBuyOnDip = isTryingToBuyDip;
    }
    
    public void setBuyOnStart(boolean buyOnStart) {
        this.buyOnStart = buyOnStart;
    }

    public boolean isBuyOnStart() {
        return buyOnStart;
    }
    
    /**
     * @return the sellOpenOrdersOnPeak
     */
    public boolean isSellOpenOrdersOnPeak() {
        return sellOpenOrdersOnPeak;
    }

    /**
     * @param sellOpenOrdersOnPeak the sellOpenOrdersOnPeak to set
     */
    public void setSellOpenOrdersOnPeak(boolean sellOpenOrdersOnPeak) {
        this.sellOpenOrdersOnPeak = sellOpenOrdersOnPeak;
    }
}
