/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.coinrating.DepthCacheProcess;
import com.evgcompany.binntrdbot.misc.CurrencyPlot;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_adm_T
 */
abstract public class AbstractTradePairProcess extends PeriodicProcessSocketUpdateThread implements ControllableOrderProcess {
    private static final Semaphore SEMAPHORE_ADD = new Semaphore(1, true);
    
    protected final TradingAPIAbstractInterface client;
    protected final mainApplication app;
    protected OrdersController ordersController = null;
    protected CoinInfoAggregator info = null;

    protected CoinFilters filter = null;
    protected final String symbol;
    protected String baseAssetSymbol;
    protected String quoteAssetSymbol;
    
    protected Long orderCID = null;
    
    private long lastBarTime = 0;
    
    protected BigDecimal tradingBalanceQuotePercent = BigDecimal.valueOf(50);
    protected BigDecimal tradingBalanceMainValue = BigDecimal.valueOf(0);
    
    protected TimeSeries series = null;
    
    protected boolean longModeAuto = true;
    protected int pyramidSize = 0;
    protected int pyramidAutoMaxSize = 100;
    protected boolean stopAfterFinish = false;
    
    private BigDecimal last_trade_profit = BigDecimal.ZERO;
    
    protected CurrencyPlot plot = null;
    
    private String barInterval = "15m";
    private int barQueryCount = 1;
    protected boolean need_bar_reset = false;
    
    private boolean do_remove_flag = false;
    
    private boolean useBuyStopLimited = false;
    private int stopBuyLimitTimeout = 120;
    private boolean useSellStopLimited = false;
    private boolean isTriedBuy = false;
    private int fullOrdersCount = 0;
    private int stopSellLimitTimeout = 1200;
    
    protected long lastOrderMillis = 0;
    protected long startMillis = 0;
    protected BigDecimal currentPrice = BigDecimal.ZERO;
    protected BigDecimal lastStrategyCheckPrice = BigDecimal.ZERO;
    
    protected BigDecimal lastBuyPrice = BigDecimal.ZERO;
    protected BigDecimal orderBaseAmount = BigDecimal.ZERO;
    protected BigDecimal orderQuoteAmount = BigDecimal.ZERO;
    protected BigDecimal orderAvgPrice = BigDecimal.ZERO;
    
    protected boolean inAPIOrder = false;
    protected boolean forceMarketOrders = false;
    
    protected final long maxProcessUpdateIntervalMillis = 10 * 60 * 1000; // 10m
    
    public AbstractTradePairProcess(TradingAPIAbstractInterface client, String pair) {
        super(pair);
        app = mainApplication.getInstance();
        symbol = pair;
        this.client = client;
        this.ordersController = OrdersController.getInstance();
        startMillis = System.currentTimeMillis();
        info = CoinInfoAggregator.getInstance();
        if (info.getClient() == null) info.setClient(client);
    }
    
    protected BigDecimal calculateInstantProfit(boolean is_enter, BigDecimal price) {
        if (
            pyramidSize == 0 || 
            (is_enter && pyramidSize > 0) || 
            (!is_enter && pyramidSize < 0) ||
            orderAvgPrice == null ||
            orderAvgPrice.compareTo(BigDecimal.ZERO) <= 0 ||
            orderBaseAmount == null ||
            orderBaseAmount.compareTo(BigDecimal.ZERO) <= 0
        ) {
            return null;
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = currentPrice;
            DepthCacheProcess process = info.getDepthProcessForPair(symbol);
            if (!process.isStopped() && process.getMillisFromLastUpdate() < maxProcessUpdateIntervalMillis) {
                if (is_enter) {
                    Map.Entry<BigDecimal, BigDecimal> bestAsk = process.getBestAsk();
                    if (bestAsk != null) price = bestAsk.getKey();
                } else {
                    Map.Entry<BigDecimal, BigDecimal> bestBid = process.getBestBid();
                    if (bestBid != null) price = bestBid.getKey();
                }
            }
        }
        BigDecimal profit;
        if (is_enter) {
            profit = orderAvgPrice.subtract(price).divide(price, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        } else {
            profit = price.subtract(orderAvgPrice).divide(orderAvgPrice, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }
        profit = profit.subtract(client.getTradeComissionPercent());
        return profit;
    }
    
    protected boolean doEnter(BigDecimal curPrice, boolean skip_check) {
        if (pyramidSize == 0) {
            longModeAuto = true;
        }
        isTriedBuy = true;
        if (curPrice == null || curPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal base_asset_amount;
        if (pyramidSize >= 0) {
            base_asset_amount = BalanceController.getInstance().getOrderAssetAmount(quoteAssetSymbol, tradingBalanceQuotePercent, baseAssetSymbol, tradingBalanceMainValue);
        } else {
            base_asset_amount = orderQuoteAmount.divide(curPrice, RoundingMode.HALF_DOWN);
        }
        filter.setCurrentPrice(curPrice);
        filter.setCurrentAmount(base_asset_amount);
        filter.prepareForBuy(!forceMarketOrders);
        BigDecimal tobuy_price = filter.getCurrentPrice();
        BigDecimal tobuy_amount = filter.getCurrentAmount();
        BigDecimal quote_asset_amount = tobuy_price.multiply(tobuy_amount);
        
        BigDecimal profit = calculateInstantProfit(true, tobuy_price);
        long result = -1;
        
        if (
                skip_check || 
                !ordersController.isLowHold() || 
                profit == null ||
                profit.compareTo(ordersController.getTradeMinProfitPercent()) > 0 ||
                (
                    ordersController.getStopLossPercent() != null &&
                    profit.compareTo(ordersController.getStopLossPercent().multiply(BigDecimal.valueOf(-1))) < 0
                )
        ) {
            if (BalanceController.getInstance().canBuy(symbol, tobuy_amount, tobuy_price)) {
                app.log("BYING " + NumberFormatter.df8.format(tobuy_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")", true, true);
                lastOrderMillis = System.currentTimeMillis();
                result = ordersController.Buy(orderCID, tobuy_amount, !forceMarketOrders ? tobuy_price : null);
                if (result < 0) {
                    app.log("Error!", true, true);
                }
            } else {
                app.log("Can't buy " + NumberFormatter.df8.format(tobuy_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")");
            }
        } else {
            app.log(symbol + " - need to enter but profit ("+NumberFormatter.df4.format(profit)+"%) is too low. Waiting...", false, true);
        }
        return result >= 0;
    }

    protected boolean doExit(BigDecimal price, boolean skip_check) {
        if (pyramidSize == 0) {
            longModeAuto = false;
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        BigDecimal base_asset_amount;
        if (pyramidSize > 0) {
            base_asset_amount = orderBaseAmount;
        } else {
            base_asset_amount = BalanceController.getInstance().getOrderAssetAmount(quoteAssetSymbol, tradingBalanceQuotePercent, baseAssetSymbol, tradingBalanceMainValue);
        }
        
        filter.setCurrentPrice(price);
        filter.setCurrentAmount(base_asset_amount);
        filter.prepareForSell(!forceMarketOrders);
        BigDecimal tosell_price = filter.getCurrentPrice();
        BigDecimal tosell_amount = filter.getCurrentAmount();
        BigDecimal quote_asset_amount = tosell_amount.multiply(tosell_price);
        
        BigDecimal profit = calculateInstantProfit(false, tosell_price);
        long result = -1;
        
        //BigDecimal incomeWithoutComission = tosell_price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(client.getTradeComissionPercent()))).subtract(lastBuyPrice);
        //BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(lastBuyPrice, RoundingMode.HALF_DOWN);
        if (
                skip_check || 
                !ordersController.isLowHold() || 
                profit == null ||
                profit.compareTo(ordersController.getTradeMinProfitPercent()) > 0 ||
                (
                    ordersController.getStopLossPercent() != null &&
                    profit.compareTo(ordersController.getStopLossPercent().multiply(BigDecimal.valueOf(-1))) < 0
                )
            ) {
            if (BalanceController.getInstance().canSell(symbol, tosell_amount)) {
                app.log("SELLING " + NumberFormatter.df8.format(tosell_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tosell_price) + ")", true, true);
                lastOrderMillis = System.currentTimeMillis();
                result = ordersController.Sell(orderCID, tosell_amount, !forceMarketOrders ? tosell_price : null, null);
                if (result < 0) {
                    app.log("Error!", true, true);
                }
            } else {
                app.log("Can't sell " + NumberFormatter.df8.format(tosell_amount) + " " + symbol + "", false, true);
            }
        } else {
            app.log(symbol + " - need to exit but profit ("+NumberFormatter.df4.format(profit)+"%) is too low. Waiting...", false, true);
        }
        return result >= 0;
    }

    protected void checkStatus() {
        info.setLatestPrice(symbol, currentPrice);
        ordersController.updatePairTradeText(orderCID);
    };
    
    private void addBars(List<Bar> nbars) {
        try {
            SEMAPHORE_ADD.acquire();
            client.addUpdateSeriesBars(series, nbars);
            if (plot != null) {
                plot.updateSeries();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(AbstractTradePairProcess.class.getName()).log(Level.SEVERE, null, ex);
        }
        SEMAPHORE_ADD.release();
    }

    protected void onBarUpdate(Bar bar, boolean is_final) {
        addBars(Arrays.asList(bar));
        if (lastBarTime < bar.getBeginTime().toInstant().toEpochMilli()) {
            lastBarTime = bar.getBeginTime().toInstant().toEpochMilli();
        }
        currentPrice = new BigDecimal(bar.getClosePrice().floatValue());
        info.setLatestPrice(symbol, currentPrice);
        ordersController.updatePairTradeText(orderCID);
    }
    
    @Override
    protected void initSocket() {
        setSocket(
            client.OnBarUpdateEvent(symbol.toLowerCase(), barInterval, this::onBarUpdate, this::socketClosed)
        );
    }
    
    protected void setNewSeries() {
        series = new BaseTimeSeries(symbol + "_SERIES");
    }
    
    protected void resetSeries() {
        need_bar_reset = false;
        setNewSeries();
        
        if (isInitialized) stopSocket();

        List<Bar> bars = client.getBars(symbol, barInterval, 500, barQueryCount);
        if (bars.size() > 0) {
            addBars(bars);
            lastBarTime = bars.get(bars.size() - 1).getBeginTime().toInstant().toEpochMilli();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClosePrice().floatValue());
            currentPrice = lastStrategyCheckPrice;
            info.setLatestPrice(symbol, currentPrice);
            ordersController.updatePairTradeText(orderCID);
        }
        
        if (isInitialized) initSocket();
    }
    protected void resetBars() {
        resetSeries();
    }
    
    private void nextBars() {
        List<Bar> bars = null;
        if (need_bar_reset) {
            resetSeries();
        } else {
            bars = client.getBars(symbol, barInterval, 500, lastBarTime, null);
            addBars(bars);
        }
        if (bars != null && bars.size() > 0) {
            lastBarTime = bars.get(bars.size() - 1).getBeginTime().toInstant().toEpochMilli();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClosePrice().floatValue());
            currentPrice = lastStrategyCheckPrice;
        }
    }
    
    protected boolean onOrderCheckEvent(
        Long pairOrderCID, 
        OrderPairItem orderPair, 
        Order order
    ) {
        if (
                need_stop || 
                !inAPIOrder ||
                order == null || 
                order.getStatus() == OrderStatus.CANCELED || 
                order.getStatus() == OrderStatus.FILLED || 
                order.getStatus() == OrderStatus.PARTIALLY_FILLED
        ) {
            return false;
        }
        if(order.getSide() == OrderSide.BUY) {
            if (useBuyStopLimited && (System.currentTimeMillis()-lastOrderMillis) > 1000*stopBuyLimitTimeout) {
                app.log("We wait too long. Need to stop this "+symbol+"'s BUY order...");
                lastOrderMillis = System.currentTimeMillis();
                return true;
            }
        } else {
            if (useSellStopLimited && (System.currentTimeMillis()-lastOrderMillis) > 1000*stopSellLimitTimeout) {
                app.log("We wait too long. Need to stop this "+symbol+"'s SELL order...");
                lastOrderMillis = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }
    
    private void addOrderQuantities(BigDecimal executedQty, BigDecimal price, boolean isBuying) {
        BigDecimal executedQuoteQty = executedQty.multiply(price);
        if (orderBaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            orderAvgPrice = price;
        }
        if((isBuying && pyramidSize >= 0) || (!isBuying && pyramidSize <= 0)) {
            if (orderBaseAmount.compareTo(BigDecimal.ZERO) > 0) {
                orderAvgPrice = orderAvgPrice.multiply(orderBaseAmount).add(price.multiply(executedQty)).divide(orderBaseAmount.add(executedQty), RoundingMode.HALF_UP);
            }
            orderBaseAmount = orderBaseAmount.add(executedQty);
            orderQuoteAmount = orderQuoteAmount.add(executedQuoteQty);
        } else {
            /*if (orderBaseAmount.compareTo(executedQty) > 0) {
                orderAvgPrice = orderAvgPrice.multiply(orderBaseAmount).subtract(price.multiply(executedQty)).divide(orderBaseAmount.subtract(executedQty), RoundingMode.HALF_UP);
            }*/
            orderBaseAmount = orderBaseAmount.subtract(executedQty);
            orderQuoteAmount = orderQuoteAmount.subtract(executedQuoteQty);
            if (orderQuoteAmount.compareTo(BigDecimal.ZERO) < 0) {
                orderQuoteAmount = BigDecimal.ZERO;
            }
            if (orderBaseAmount.compareTo(BigDecimal.ZERO) < 0) {
                orderBaseAmount = BigDecimal.ZERO;
            }
        }
        if (orderBaseAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (isBuying) {
                pyramidSize++;
            } else {
                pyramidSize--;
            }
        } else {
            pyramidSize = 0;
        }
    }
    
    protected void onBuySell(boolean isBuying, BigDecimal price, BigDecimal executedQty) {
        // nothing
    }
    
    protected void onOrderEvent(
            Long pairOrderCID, 
            OrderPairItem orderPair, 
            boolean isNew, 
            boolean isBuying, 
            boolean isCancelled, 
            boolean isFinished, 
            BigDecimal price, 
            BigDecimal qty, 
            BigDecimal executedQty) 
    {
        if (isNew) {
            inAPIOrder = true;
            if (isBuying) {
                app.log("Buying "+symbol+"...", true, true);
            } else {
                app.log("Selling "+symbol+"...", true, true);
            }
            lastOrderMillis = System.currentTimeMillis();
        }
        if (!isFinished) {
            return;
        }
        
        inAPIOrder = false;
        
        if (isCancelled || executedQty.compareTo(qty) < 0) {
            // just cancelled or partially filled and then cancelled
            if (executedQty.compareTo(BigDecimal.ZERO) <= 0) {
                app.log("Order for "+(isBuying?"buy":"sell")+" "+symbol+" is cancelled!", true, true);
                ordersController.finishOrder(orderCID, false, orderAvgPrice);
            } else {
                if(isBuying) {
                    lastBuyPrice = price;
                }
                addOrderQuantities(executedQty, price, isBuying);
                ordersController.finishOrderPart(orderCID, orderAvgPrice, executedQty);
                ordersController.finishOrder(orderCID, false, orderAvgPrice);
                app.log("Order for "+(isBuying?"buy":"sell")+" "+symbol+" is partially finished! Price = " + NumberFormatter.df8.format(price) + "; Quantity = " + NumberFormatter.df8.format(executedQty), true, true);
            }

        } else {
            // filled
            
            String profitStr = "";
            
            if(isBuying) {
                lastBuyPrice = price;
                onBuySell(true, price, executedQty);
            } else {
                onBuySell(false, price, executedQty);
                
                /*BigDecimal incomeWithoutComission = price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(client.getTradeComissionPercent()))).subtract(lastBuyPrice);
                BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(lastBuyPrice, RoundingMode.HALF_DOWN);
                last_trade_profit = incomeWithoutComissionPercent;
                profitStr = "; Profit = " + NumberFormatter.df4.format(incomeWithoutComissionPercent) + "%";*/
            }
            addOrderQuantities(executedQty, price, isBuying);

            lastOrderMillis = System.currentTimeMillis();

            ordersController.finishOrder(orderCID, true, orderAvgPrice);
            app.log("Order for "+(isBuying?"buy":"sell")+" "+symbol+" is finished! Price = "+NumberFormatter.df8.format(price) + profitStr, true, true);
            
            if (pyramidSize == 0) {
                fullOrdersCount++;
                if (stopAfterFinish) doStop();
            }
        }
        
        System.out.println(symbol + ":  PYR_SIZE " + pyramidSize + "  BASE " + orderBaseAmount + "  QUOTE " + orderQuoteAmount + "  AVG " + orderAvgPrice);
    }
    
    @Override
    protected void runStart() {

        doWait(ThreadLocalRandom.current().nextLong(100, 500));

        app.log("thread for " + symbol + " is running...");
        app.log("");
        
        BalanceController.getInstance().StartAndWaitForInit();
        info.startDepthCheckForPair(symbol);
        
        try {
            filter = info.getPairFilters().get(symbol);
            filter.logFiltersInfo();
            baseAssetSymbol = filter.getBaseAssetSymbol();
            quoteAssetSymbol = filter.getQuoteAssetSymbol();
        } catch (Exception e) {
            app.log("symbol " + symbol + " error. Exiting from thread...");
            doStop();
            return;
        }

        orderCID = ordersController.registerPairTrade(symbol, this, this::onOrderEvent, this::onOrderCheckEvent);

        doWait(startDelayTime);
        
        resetSeries();
        startMillis = System.currentTimeMillis();
    }
    
    @Override
    protected void runBody() {
        nextBars();
        if (!inAPIOrder) {
            checkStatus();
        }
    }

    @Override
    protected void runFinish() {
        info.stopDepthCheckForPair(symbol);
        ordersController.unregisterPairTrade(orderCID);
        app.log("");
        app.log("thread for " + symbol + " is stopped...");
    }

    @Override
    public void doBuy() {
        if (!inAPIOrder) {
            doEnter(currentPrice, true);
        }
    }

    @Override
    public void doSell() {
        if (!inAPIOrder) {
            doExit(currentPrice, true);
        }
    }

    @Override
    public void orderCancel() {
        ordersController.cancelOrder(orderCID);
    }
    
    public void doShowPlot() {
        plot = new CurrencyPlot(symbol, series, null, null);
        plot.showPlot();
    }
    
    /**
     * @param _barInterval the barInterval to set
     */
    public void setBarInterval(String _barInterval) {
        if (
                "1m".equals(_barInterval) || 
                "5m".equals(_barInterval) || 
                "15m".equals(_barInterval) || 
                "30m".equals(_barInterval) || 
                "1h".equals(_barInterval) || 
                "2h".equals(_barInterval)
        ) {
            if (!barInterval.equals(_barInterval) && series != null && series.getBarCount() > 0) {
                need_bar_reset = true;
            }
            barInterval = _barInterval;
        }
    }
    
    /**
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }
    public String getBaseSymbol() {
        return baseAssetSymbol;
    }
    public String getQuoteSymbol() {
        return quoteAssetSymbol;
    }
    
    /**
     * @return the do_remove_flag
     */
    public boolean needToRemove() {
        return do_remove_flag;
    }

    /**
     * @param do_remove_flag the do_remove_flag to set
     */
    public void setNeedToRemove(boolean do_remove_flag) {
        this.do_remove_flag = do_remove_flag;
    }

    /**
     * @return the tradingBalancePercent
     */
    public BigDecimal getTradingBalanceQuotePercent() {
        return tradingBalanceQuotePercent;
    }

    /**
     * @param tradingBalancePercent the tradingBalancePercent to set
     */
    public void setTradingBalancePercent(int tradingBalancePercent) {
        this.tradingBalanceQuotePercent = BigDecimal.valueOf(tradingBalancePercent);
    }

    /**
     * @return the useStopLimited
     */
    public boolean isUseBuyStopLimited() {
        return useBuyStopLimited;
    }

    /**
     * @param useBuyStopLimited the useStopLimited to set
     */
    public void setUseBuyStopLimited(boolean useBuyStopLimited) {
        this.useBuyStopLimited = useBuyStopLimited;
    }

    /**
     * @return the stopLimitTimeout
     */
    public int getStopBuyLimitTimeout() {
        return stopBuyLimitTimeout;
    }

    /**
     * @param stopBuyLimitTimeout the stopLimitTimeout to set
     */
    public void setStopBuyLimitTimeout(int stopBuyLimitTimeout) {
        this.stopBuyLimitTimeout = stopBuyLimitTimeout;
    }

    /**
     * @return the useSellStopLimited
     */
    public boolean isUseSellStopLimited() {
        return useSellStopLimited;
    }

    /**
     * @param useSellStopLimited the useSellStopLimited to set
     */
    public void setUseSellStopLimited(boolean useSellStopLimited) {
        this.useSellStopLimited = useSellStopLimited;
    }

    /**
     * @return the stopSellLimitTimeout
     */
    public int getStopSellLimitTimeout() {
        return stopSellLimitTimeout;
    }

    /**
     * @param stopSellLimitTimeout the stopSellLimitTimeout to set
     */
    public void setStopSellLimitTimeout(int stopSellLimitTimeout) {
        this.stopSellLimitTimeout = stopSellLimitTimeout;
    }

    /**
     * @return the barQueryCount
     */
    public int getBarQueryCount() {
        return barQueryCount;
    }

    /**
     * @param barQueryCount the barQueryCount to set
     */
    public void setBarQueryCount(int barQueryCount) {
        this.barQueryCount = barQueryCount;        
    }
    
    /**
     * @return the isTriedBuy
     */
    public boolean isTriedBuy() {
        return isTriedBuy;
    }
    
    public boolean isInAPIOrder() {
        return inAPIOrder;
    }

    /**
     * @return the last_trade_profit
     */
    public BigDecimal getLastTradeProfit() {
        return last_trade_profit;
    }

    /**
     * @return the startMillis
     */
    public long getStartMillis() {
        return startMillis;
    }

    /**
     * @return the fullOrdersCount
     */
    public int getFullOrdersCount() {
        return fullOrdersCount;
    }
    
    public BigDecimal getLastPrice() {
        return currentPrice;
    }

    /**
     * @return the orderCID
     */
    public Long getOrderCID() {
        return orderCID;
    }

    public boolean isInOrder() {
        return pyramidSize != 0;
    }

    /**
     * @return the longModeAuto
     */
    public boolean isLongModeAuto() {
        return longModeAuto;
    }

    /**
     * @param longModeAuto the longModeAuto to set
     */
    public void setLongModeAuto(boolean longModeAuto) {
        this.longModeAuto = longModeAuto;
    }

    /**
     * @return the pyramidAutoMaxSize
     */
    public int getPyramidAutoMaxSize() {
        return pyramidAutoMaxSize;
    }

    /**
     * @param pyramidAutoMaxSize the pyramidAutoMaxSize to set
     */
    public void setPyramidAutoMaxSize(int pyramidAutoMaxSize) {
        this.pyramidAutoMaxSize = pyramidAutoMaxSize;
    }

    /**
     * @return the pyramidSize
     */
    public int getPyramidSize() {
        return pyramidSize;
    }

    /**
     * @return the tradingBalanceMainValue
     */
    public BigDecimal getTradingBalanceMainValue() {
        return tradingBalanceMainValue;
    }

    /**
     * @param tradingBalanceMainValue the tradingBalanceMainValue to set
     */
    public void setTradingBalanceMainValue(double tradingBalanceMainValue) {
        this.tradingBalanceMainValue = BigDecimal.valueOf(tradingBalanceMainValue);
    }
}
