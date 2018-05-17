/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.evgcompany.binntrdbot.analysis.NeuralNetworkStockPredictor;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.misc.CurrencyPlot;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import com.evgcompany.binntrdbot.signal.SignalItem;
import com.evgcompany.binntrdbot.strategies.core.StrategiesController;
import com.evgcompany.binntrdbot.strategies.core.StrategyItem;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_adm_T
 */
public class TradePairProcess extends PeriodicProcessThread implements ControllableOrderProcess {
    private static final Semaphore SEMAPHORE_ADD = new Semaphore(1, true);
    
    protected final TradingAPIAbstractInterface client;
    protected final mainApplication app;
    protected OrdersController ordersController = null;
    protected StrategiesController strategiesController = null;
    protected CoinInfoAggregator info = null;
    private NeuralNetworkStockPredictor predictor = null;

    protected CoinFilters filter = null;
    protected final String symbol;
    protected String baseAssetSymbol;
    protected String quoteAssetSymbol;
    
    protected Long orderCID = null;
    
    private boolean isTryingToSellOnPeak = false;
    private boolean isTryingToBuyOnDip = false;
    private boolean stopAfterSell = false;
    private boolean sellOpenOrdersOnPeak = false;
    private boolean checkOtherStrategies = true;
    
    private long lastBarTime = 0;
    
    private BigDecimal tradingBalanceQuotePercent = BigDecimal.valueOf(50);
    private BigDecimal tradingBalanceMainValue = BigDecimal.valueOf(0);
    
    private TimeSeries series = null;
    
    private boolean inLong = false;     // Buy for sell on peak
    private boolean inShort = false;    // Sell for buy on dip
    private boolean longMode = true;    // Auto long orders
    
    private int pyramidSize = 0;
    private int pyramidAutoMaxSize = 100;
    
    private BigDecimal last_trade_profit = BigDecimal.ZERO;
    
    private CurrencyPlot plot = null;
    
    private String barInterval = "15m";
    private int barQueryCount = 1;
    private boolean need_bar_reset = false;
    private Closeable socket = null;
    
    private boolean buyOnStart = false;
    
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
    
    private SignalItem signalItem = null;
    
    protected boolean inAPIOrder = false;
    
    public TradePairProcess(TradingAPIAbstractInterface rclient, String pair) {
        app = mainApplication.getInstance();
        symbol = pair;
        client = rclient;
        this.ordersController = OrdersController.getInstance();
        strategiesController = new StrategiesController(symbol);
        startMillis = System.currentTimeMillis();
        info = CoinInfoAggregator.getInstance();
        if (info.getClient() == null) info.setClient(client);
    }

    @Override
    public void finalize() throws Throwable {
        if (socket != null) {
            socket.close();
        }
        super.finalize();
    }
    
    private void doEnter(BigDecimal curPrice) {
        if (!inShort && !inLong) {
            longMode = true;
        }
        isTriedBuy = true;
        if (curPrice == null || curPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal base_asset_amount;
        if (longMode) {
            base_asset_amount = BalanceController.getInstance().getOrderAssetAmount(quoteAssetSymbol, tradingBalanceQuotePercent, baseAssetSymbol, tradingBalanceMainValue);
        } else {
            base_asset_amount = orderQuoteAmount.divide(curPrice, RoundingMode.HALF_DOWN);
        }
        filter.setCurrentPrice(curPrice);
        filter.setCurrentAmount(base_asset_amount);
        filter.prepareForBuy();
        BigDecimal tobuy_price = filter.getCurrentPrice();
        BigDecimal tobuy_amount = filter.getCurrentAmount();
        BigDecimal quote_asset_amount = tobuy_price.multiply(tobuy_amount);
        if (BalanceController.getInstance().canBuy(symbol, tobuy_amount, tobuy_price)) {
            app.log("BYING " + NumberFormatter.df8.format(tobuy_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")", true, true);
            lastOrderMillis = System.currentTimeMillis();
            long result = ordersController.Buy(orderCID, tobuy_amount, tobuy_price);
            if (result < 0) {
                app.log("Error!", true, true);
            }
        } else {
            app.log("Can't buy " + NumberFormatter.df8.format(tobuy_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")");
        }
    }
    private void doExit(BigDecimal price, boolean skip_check) {
        if (!inShort && !inLong) {
            longMode = false;
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        
        BigDecimal base_asset_amount;
        if (longMode) {
            base_asset_amount = orderBaseAmount;
        } else {
            base_asset_amount = BalanceController.getInstance().getOrderAssetAmount(quoteAssetSymbol, tradingBalanceQuotePercent, baseAssetSymbol, tradingBalanceMainValue);
        }
        filter.setCurrentPrice(price);
        filter.setCurrentAmount(base_asset_amount);
        filter.prepareForSell();
        BigDecimal tosell_price = filter.getCurrentPrice();
        BigDecimal tosell_amount = filter.getCurrentAmount();
        BigDecimal quote_asset_amount = tosell_amount.multiply(tosell_price);
        //BigDecimal incomeWithoutComission = tosell_price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(client.getTradeComissionPercent()))).subtract(lastBuyPrice);
        //BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(lastBuyPrice, RoundingMode.HALF_DOWN);
        if (
                skip_check || 
                !ordersController.isLowHold() || 
                signalItem != null /*||
                incomeWithoutComissionPercent.compareTo(ordersController.getTradeMinProfitPercent()) > 0 ||
                (
                    ordersController.getStopLossPercent() != null &&
                    incomeWithoutComissionPercent.compareTo(ordersController.getStopLossPercent().multiply(BigDecimal.valueOf(-1))) < 0
                )*/
            ) {
            if (BalanceController.getInstance().canSell(symbol, tosell_amount)) {
                app.log("SELLING " + NumberFormatter.df8.format(tosell_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tosell_price) + ")", true, true);
                lastOrderMillis = System.currentTimeMillis();
                long result = ordersController.Sell(orderCID, tosell_amount, tosell_price, null);
                if (result < 0) {
                    app.log("Error!", true, true);
                }
            } else {
                app.log("Can't sell " + NumberFormatter.df8.format(tosell_amount) + " " + symbol + "", false, true);
            }
        } else {
            //app.log(symbol + " - need to exit but profit ("+NumberFormatter.df4.format(incomeWithoutComissionPercent)+"%) is too low. Waiting...", false, true);
        }
    }

    private boolean canBuyForCoinRating() {
        return app.getCoinRatingController() == null || 
                !app.getCoinRatingController().isAlive() || 
                !app.getCoinRatingController().isNoAutoBuysOnDowntrend() || 
                !app.getCoinRatingController().isInDownTrend();
    }

    private void checkStatus() {
        StrategiesController.StrategiesAction saction = strategiesController.checkStatus(
            (longMode && inLong) || (!longMode && !inShort), 
            checkOtherStrategies
        );
        if (saction == StrategiesController.StrategiesAction.DO_ENTER && pyramidSize < pyramidAutoMaxSize) {
            if (canBuyForCoinRating()) doEnter(lastStrategyCheckPrice);
        } else if (saction == StrategiesController.StrategiesAction.DO_EXIT && pyramidSize > -pyramidAutoMaxSize) {
            doExit(lastStrategyCheckPrice, false);
        }
        info.setLatestPrice(symbol, currentPrice);
        ordersController.updatePairTradeText(orderCID);
    }
    
    private void addBars(List<Bar> nbars) {
        try {
            SEMAPHORE_ADD.acquire();
            client.addUpdateSeriesBars(series, nbars);
            if (plot != null) {
                plot.updateSeries();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(TradePairProcess.class.getName()).log(Level.SEVERE, null, ex);
        }
        SEMAPHORE_ADD.release();
    }
    
    private void initSellAllOnPeak() {
        Trade last = client.getLastTrade(symbol);
        if (last != null) {
            BigDecimal lastTradeBuyPrice = new BigDecimal(last.getPrice());
            
            /*BigDecimal currentLimitSellPrice = BigDecimal.ZERO;
            BigDecimal currentLimitSellQty = BigDecimal.ZERO;*/
            
            CoinBalanceItem qbalance = BalanceController.getInstance().getCoinBalanceInfo(baseAssetSymbol);
            BigDecimal free_cnt = qbalance.getFreeValue();
            BigDecimal order_cnt = BigDecimal.ZERO;
            
            List<Long> ordersToCancel = new ArrayList<>();

            if (sellOpenOrdersOnPeak) {
                List<Order> openOrders = client.getOpenOrders(symbol);
                for (int i=0; i < openOrders.size(); i++) {
                    if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL) {
                        ordersToCancel.add(openOrders.get(i).getOrderId());
                        order_cnt = order_cnt.add(new BigDecimal(openOrders.get(i).getOrigQty()));
                    }
                }
                /*for (int i=0; i < openOrders.size(); i++) {
                    if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL && openOrders.get(i).getType() == OrderType.LIMIT) {
                        currentLimitSellPrice = new BigDecimal(openOrders.get(i).getPrice());
                        currentLimitSellQty = new BigDecimal(openOrders.get(i).getOrigQty());
                    }
                }*/
            }

            if (free_cnt.compareTo(BigDecimal.ZERO) > 0 || (order_cnt.compareTo(BigDecimal.ZERO) > 0 && !ordersToCancel.isEmpty())) {
                if (!ordersToCancel.isEmpty()) {
                    app.log("Canceling orders: " + ordersToCancel, true, true);
                    ordersController.cancelOrdersList(orderCID, ordersToCancel);
                    doWait(10000);
                    qbalance = BalanceController.getInstance().getCoinBalanceInfo(baseAssetSymbol);
                    free_cnt = qbalance.getFreeValue();
                }
                if (free_cnt.compareTo(BigDecimal.ZERO) > 0) {
                    boolean result = ordersController.PreBuySell(orderCID, free_cnt, lastTradeBuyPrice/*, currentLimitSellQty, currentLimitSellPrice*/);
                    if (result) {
                        app.log("START WAITING to sell " + NumberFormatter.df8.format(free_cnt) + " " + baseAssetSymbol + " for price more than " + NumberFormatter.df8.format(lastTradeBuyPrice) + " " + quoteAssetSymbol, true, true);
                        inAPIOrder = false;
                        lastBuyPrice = lastTradeBuyPrice;
                        orderBaseAmount = free_cnt;
                        orderQuoteAmount = free_cnt.multiply(lastTradeBuyPrice);
                        orderAvgPrice = lastTradeBuyPrice;
                        stopAfterSell = true;
                        longMode = true;
                        inLong = true;
                        pyramidSize = 1;
                        inShort = false;
                        ordersController.updatePairTradeText(orderCID);
                        return;
                    } else {
                        app.log("Error in PreBuy method!", true, true);
                    }
                }
            }
        }
        isTryingToSellOnPeak = false;
        app.log("Can't set SellAllPeak mode for " + symbol, true, true);
    } 
    
    private void stopSockets() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(TradePairProcess.class.getName()).log(Level.SEVERE, null, ex);
            }
            socket = null;
        }
    }

    private void resetSeries() {
        need_bar_reset = false;
        series = strategiesController.resetSeries();
        
        if (strategiesController.getMainStrategy().equals("Neural Network")) {
            predictor = new NeuralNetworkStockPredictor(symbol);
            if (!predictor.isHaveNetworkInFile() && predictor.isHaveNetworkInBaseFile()) {
                predictor.toBase();
            }
        }
        
        stopSockets();

        List<Bar> bars = client.getBars(symbol, barInterval, 500, barQueryCount);
        if (bars.size() > 0) {
            addBars(bars);
            lastBarTime = bars.get(bars.size() - 1).getBeginTime().toInstant().toEpochMilli();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClosePrice().floatValue());
            currentPrice = lastStrategyCheckPrice;
            info.setLatestPrice(symbol, currentPrice);
            ordersController.updatePairTradeText(orderCID);
        }

        if (predictor != null && predictor.isHaveNetworkInFile()) {
            predictor.initMinMax(series);
        }
        
        socket = client.OnBarUpdateEvent(symbol.toLowerCase(), barInterval, (nbar, is_fin) -> {
            addBars(Arrays.asList(nbar));
            if (lastBarTime < nbar.getBeginTime().toInstant().toEpochMilli()) {
                lastBarTime = nbar.getBeginTime().toInstant().toEpochMilli();
            }
            currentPrice = new BigDecimal(nbar.getClosePrice().floatValue());
            info.setLatestPrice(symbol, currentPrice);
            ordersController.updatePairTradeText(orderCID);
            if (is_fin && (predictor == null || !predictor.isLearning())) {
                app.log(symbol + " current price = " + NumberFormatter.df8.format(currentPrice));
            }
        });
    }

    private void nextBars() {
        List<Bar> bars = null;
        if (need_bar_reset) {
            resetSeries();
            if (signalItem != null) {
                setMainStrategy("Signal");
                strategiesController.startSignal(signalItem);
            }
            strategiesController.resetStrategies();
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
    
    public void doNetworkAction(String train, String base) {
        if (strategiesController.getMainStrategy().equals("Neural Network")) {
            predictor = new NeuralNetworkStockPredictor(base.equals("COIN") ? symbol : "");
            switch (train) {
                case "TRAIN":
                    predictor.start();
                    break;
                case "ADDSET":
                    predictor.setSaveTrainData(true);
                    predictor.appendTrainingData(series);
                    break;
                case "STOP":
                    if (predictor.getLearningRule() != null) {
                        predictor.getLearningRule().stopLearning();
                    }   break;
                default:
                    break;
            }
        }
    }
    
    private boolean onOrderCheckEvent(
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
        if(isBuying && longMode || !isBuying && !longMode) {
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
        if (isBuying) {
            pyramidSize++;
        } else {
            pyramidSize--;
        }
    }
    
    private void onOrderEvent(
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
                inLong = longMode;
                inShort = false;
            } else {
                app.log("Selling "+symbol+"...", true, true);
                inLong = false;
                inShort = !longMode;
                isTryingToSellOnPeak = false;
            }
            lastOrderMillis = System.currentTimeMillis();
        }
        if (!isFinished) {
            return;
        }
        
        inAPIOrder = false;
        
        if (isCancelled || executedQty.compareTo(qty) < 0) {
            // just cancelled or partially filled and then cancelled
            if(isBuying) {
                isTryingToSellOnPeak = false;
                inLong = false;
                inShort = !longMode;
            } else {
                inLong = longMode;
                inShort = false;
            }

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
                strategiesController.getTradingRecord().enter(series.getBarCount()-1, Decimal.valueOf(price), Decimal.valueOf(executedQty));
            } else {
                strategiesController.getTradingRecord().exit(series.getBarCount()-1, Decimal.valueOf(price), Decimal.valueOf(executedQty));
                fullOrdersCount++;
                /*BigDecimal incomeWithoutComission = price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(client.getTradeComissionPercent()))).subtract(lastBuyPrice);
                BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(lastBuyPrice, RoundingMode.HALF_DOWN);
                last_trade_profit = incomeWithoutComissionPercent;
                profitStr = "; Profit = " + NumberFormatter.df4.format(incomeWithoutComissionPercent) + "%";*/
            }
            addOrderQuantities(executedQty, price, isBuying);

            lastOrderMillis = System.currentTimeMillis();

            ordersController.finishOrder(orderCID, true, orderAvgPrice);
            app.log("Order for "+(isBuying?"buy":"sell")+" "+symbol+" is finished! Price = "+NumberFormatter.df8.format(price) + profitStr, true, true);
            if (stopAfterSell && !isBuying) {
                doStop();
            }
        }
        
        System.out.println("PYR_SIZE " + pyramidSize + "  BASE " + orderBaseAmount + "  QUOTE " + orderQuoteAmount + "  AVG " + orderAvgPrice);
    }
    
    @Override
    protected void runStart() {

        if (!buyOnStart) {
            doWait(ThreadLocalRandom.current().nextLong(100, 500));
        }

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

        if (!buyOnStart) {
            doWait(startDelayTime);
        }
        
        resetSeries();
        if (signalItem != null) {
            setMainStrategy("Signal");
            strategiesController.startSignal(signalItem);
        }
        strategiesController.resetStrategies();
        
        if (isTryingToSellOnPeak) {
            initSellAllOnPeak();
        }
        
        startMillis = System.currentTimeMillis();

        if (buyOnStart && canBuyForCoinRating() && !strategiesController.isShouldLeave()) {
            doBuy();
        }

        app.log(symbol + " initialized. Current price = " + NumberFormatter.df8.format(currentPrice));
    }
    
    @Override
    protected void runBody() {
        nextBars();
        if(strategiesController.getMainStrategy().equals("Neural Network")) {
            if (predictor != null && !predictor.isLearning() && predictor.isHaveNetworkInFile()) {
                Bar nbar = predictor.predictNextBar(series);
                app.log(symbol + " NEUR prediction = " + NumberFormatter.df8.format(nbar.getClosePrice()));
            }
        }
        if (!inAPIOrder) {
            checkStatus();
        }
    }

    @Override
    protected void runFinish() {
        stopSockets();
        info.stopDepthCheckForPair(symbol);
        ordersController.unregisterPairTrade(orderCID);
        app.log("");
        app.log("thread for " + symbol + " is stopped...");
    }

    @Override
    public void doBuy() {
        if (!inAPIOrder) {
            doEnter(currentPrice);
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
        StrategyItem item = strategiesController.getMainStrategyItem();
        plot = new CurrencyPlot(symbol, series, strategiesController.getTradingRecord(), item != null ? item.getInitializer() : null);
        for(int i=0; i<strategiesController.getStrategyMarkers().size(); i++) {
            plot.addMarker(
                strategiesController.getStrategyMarkers().get(i).label, 
                strategiesController.getStrategyMarkers().get(i).timeStamp, 
                strategiesController.getStrategyMarkers().get(i).typeIndex
            );
        }
        
        if (predictor != null && predictor.isHaveNetworkInFile()) {
            List<Bar> pbars = predictor.predictPreviousBars(series);
            pbars.forEach((pbar)->{
                plot.addPoint(pbar.getEndTime().toInstant().toEpochMilli(), pbar.getClosePrice().doubleValue());
            });
        }
        plot.showPlot();
    }
    
    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        if (signalItem != null) {
            mainStrategy = "Signal";
        }
        strategiesController.setMainStrategy(mainStrategy);
        if (!mainStrategy.equals("Signal")) {
            need_bar_reset = true;
            if (predictor != null) {
                if (predictor.getLearningRule() != null) {
                    predictor.getLearningRule().stopLearning();
                }
                predictor = null;
            }
        }
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
    
    public void doShowStatistics() {
        strategiesController.logStatistics(false);
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

    public void setBuyOnStart(boolean buyOnStart) {
        this.buyOnStart = buyOnStart;
    }

    public boolean isBuyOnStart() {
        return buyOnStart;
    }

    public void setCheckOtherStrategies(boolean checkOtherStrategies) {
        this.checkOtherStrategies = checkOtherStrategies;
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
    
    public SignalItem getSignalItem() {
        return signalItem;
    }
    
    public void setSignalItem(SignalItem item) {
        signalItem = item;
    }

    /**
     * @return the orderCID
     */
    public Long getOrderCID() {
        return orderCID;
    }

    /**
     * @return the inLong
     */
    public boolean isInLong() {
        return inLong;
    }

    /**
     * @return the inShort
     */
    public boolean isInShort() {
        return inShort;
    }

    /**
     * @return the longMode
     */
    public boolean isLongMode() {
        return longMode;
    }

    /**
     * @param longMode the longMode to set
     */
    public void setLongMode(boolean longMode) {
        this.longMode = longMode;
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
