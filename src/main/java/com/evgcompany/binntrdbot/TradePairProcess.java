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
    private final mainApplication app;
    private OrdersController ordersController = null;
    private StrategiesController strategiesController = null;
    private CoinInfoAggregator info = null;
    private NeuralNetworkStockPredictor predictor = null;

    private CoinFilters filter = null;
    private final String symbol;
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    
    private Long orderCID = null;
    
    private boolean isTryingToSellUp = false;
    private boolean isTryingToBuyDip = false;
    private boolean stopAfterSell = false;
    private boolean sellUpAll = false;
    private boolean checkOtherStrategies = true;
    
    private final List<Long> orderToCancelOnSellUp = new ArrayList<>();
    private long last_time = 0;
    
    private BigDecimal tradingBalancePercent = new BigDecimal("50");
    
    private TimeSeries series = null;
    
    private boolean is_hodling = false;
    private BigDecimal last_trade_profit = BigDecimal.ZERO;
    
    private CurrencyPlot plot = null;
    
    private String barInterval = "15m";
    private int barQueryCount = 1;
    private boolean need_bar_reset = false;
    private Closeable socket = null;
    
    private boolean buyOnStart = false;
    
    private boolean base_strategy_sell_ignored = false;
    private boolean do_remove_flag = false;
    
    private boolean useBuyStopLimited = false;
    private int stopBuyLimitTimeout = 120;
    private boolean useSellStopLimited = false;
    private boolean isTriedBuy = false;
    private int fullOrdersCount = 0;
    private int stopSellLimitTimeout = 1200;
    
    private long lastOrderMillis = 0;
    private long startMillis = 0;
    private BigDecimal currentPrice = BigDecimal.ZERO;
    private BigDecimal lastStrategyCheckPrice = BigDecimal.ZERO;
    
    private BigDecimal lastBuyPrice = BigDecimal.ZERO;
    private BigDecimal lastBuyAmount = BigDecimal.ZERO;
    
    private SignalItem init_signal = null;
    
    private boolean inAPIOrder = false;
    
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
        isTriedBuy = true;
        if (curPrice == null || curPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal quote_asset_amount = BalanceController.getInstance().getOrderAssetAmount(quoteAssetSymbol, tradingBalancePercent);
        filter.setCurrentPrice(curPrice);
        filter.setCurrentAmount(quote_asset_amount);
        filter.prepareForBuy();
        BigDecimal tobuy_price = filter.getCurrentPrice();
        BigDecimal tobuy_amount = filter.getCurrentAmount();
        quote_asset_amount = tobuy_price.multiply(tobuy_amount);
        base_strategy_sell_ignored = false;
        if (BalanceController.getInstance().canBuy(symbol, tobuy_amount, tobuy_price)) {
            app.log("BYING " + NumberFormatter.df8.format(tobuy_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")", true, true);
            long result = ordersController.Buy(orderCID, tobuy_amount, tobuy_price);
            if (result < 0) {
                app.log("Error!", true, true);
            }
        } else {
            app.log("Can't buy " + NumberFormatter.df8.format(tobuy_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")");
        }
    }
    private void doExit(BigDecimal price, boolean skip_check) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        filter.setCurrentPrice(price);
        filter.setCurrentAmount(lastBuyAmount);
        filter.prepareForSell();
        BigDecimal tosell_price = filter.getCurrentPrice();
        BigDecimal tosell_amount = filter.getCurrentAmount();
        BigDecimal quote_asset_amount = tosell_amount.multiply(tosell_price);
        BigDecimal incomeWithoutComission = tosell_price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(client.getTradeComissionPercent()))).subtract(lastBuyPrice);
        BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(lastBuyPrice, RoundingMode.HALF_DOWN);
        if (
                skip_check || 
                !ordersController.isLowHold() || 
                init_signal != null ||
                incomeWithoutComissionPercent.compareTo(ordersController.getTradeMinProfitPercent()) > 0 ||
                (
                    ordersController.getStopLossPercent() != null &&
                    incomeWithoutComissionPercent.compareTo(ordersController.getStopLossPercent().multiply(BigDecimal.valueOf(-1))) < 0
                )
            ) {
            if (BalanceController.getInstance().canSell(symbol, tosell_amount)) {
                base_strategy_sell_ignored = false;
                app.log("SELLING " + NumberFormatter.df8.format(tosell_amount) + " " + baseAssetSymbol + "  for  " + NumberFormatter.df8.format(quote_asset_amount) + " " + quoteAssetSymbol + " (price=" + NumberFormatter.df8.format(tosell_price) + ")", true, true);
                long result = ordersController.Sell(orderCID, tosell_amount, tosell_price, orderToCancelOnSellUp);
                if (result < 0) {
                    app.log("Error!", true, true);
                }
            } else {
                app.log("Can't sell " + NumberFormatter.df8.format(tosell_amount) + " " + symbol + "", false, true);
            }
        } else {
            app.log(symbol + " - need to exit but profit ("+NumberFormatter.df4.format(incomeWithoutComissionPercent)+"%) is too low. Waiting...", false, true);
            base_strategy_sell_ignored = true;
        }
    }
    
    /*private void checkOrder() {
        if (ordersController.isTestMode()) {
            ordersController.finishOrder(orderCID, true, currentPrice);
            return;
        }
        Order order = client.getOrderStatus(symbol, limitOrderId);
        if(order != null) {
            System.out.println(order);
            if (order.getStatus() == OrderStatus.PARTIALLY_FILLED && Double.parseDouble(order.getExecutedQty()) < Double.parseDouble(order.getOrigQty())) {
                return;
            }
            if (
                order.getStatus() == OrderStatus.FILLED || 
                order.getStatus() == OrderStatus.PARTIALLY_FILLED || 
                order.getStatus() == OrderStatus.CANCELED ||
                order.getStatus() == OrderStatus.EXPIRED ||
                order.getStatus() == OrderStatus.REJECTED
            ) {
                limitOrderId = 0;
                if (
                        order.getStatus() != OrderStatus.FILLED ||
                        Double.parseDouble(order.getExecutedQty()) < Double.parseDouble(order.getOrigQty())
                    ) 
                {
                    if(order.getSide() == OrderSide.BUY) {
                        isTryingToSellUp = false;
                        is_hodling = false;
                        orderToCancelOnSellUp.clear();
                        base_strategy_sell_ignored = false;
                    } else {
                        is_hodling = true;
                    }
                    if (
                            (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.PARTIALLY_FILLED) &&
                            Double.parseDouble(order.getExecutedQty()) < Double.parseDouble(order.getOrigQty())
                        ) 
                    {
                        ordersController.finishOrderPart(orderCID, new BigDecimal(order.getPrice()), new BigDecimal(order.getExecutedQty()));
                        sold_amount = sold_amount.subtract(new BigDecimal(order.getExecutedQty()));
                        app.log("Limit order for "+order.getSide().name().toLowerCase()+" "+symbol+" is partially finished! Price = "+order.getPrice() + "; Quantity = " + order.getExecutedQty(), true, true);
                        BalanceController.getInstance().updateAllBalances();
                        return;
                    }
                } else {
                    if(order.getSide() == OrderSide.BUY) {
                        strategiesController.getTradingRecord().enter(series.getBarCount()-1, Decimal.valueOf(order.getPrice()), Decimal.valueOf(sold_amount));
                    } else {
                        strategiesController.getTradingRecord().exit(series.getBarCount()-1, Decimal.valueOf(order.getPrice()), Decimal.valueOf(sold_amount));
                        fullOrdersCount++;
                    }
                }
                ordersController.finishOrder(orderCID, order.getStatus() == OrderStatus.FILLED, new BigDecimal(order.getPrice()));
                BalanceController.getInstance().updateAllBalances();
                app.log("Limit order for "+order.getSide().name().toLowerCase()+" "+symbol+" is finished! Status="+order.getStatus().name()+"; Price = "+order.getPrice(), true, true);
                if (stopAfterSell && order.getSide() == OrderSide.SELL && order.getStatus() == OrderStatus.FILLED) {
                    doStop();
                }
            } else {
                if(order.getSide() == OrderSide.BUY) {
                    if (useBuyStopLimited && (System.currentTimeMillis()-lastOrderMillis) > 1000*stopBuyLimitTimeout) {
                        app.log("We wait too long. Need to stop this "+symbol+"'s BUY order...");
                        lastOrderMillis = System.currentTimeMillis();
                        doLimitCancel();
                        return;
                    }
                } else {
                    if (useSellStopLimited && (System.currentTimeMillis()-lastOrderMillis) > 1000*stopSellLimitTimeout) {
                        app.log("We wait too long. Need to stop this "+symbol+"'s SELL order...");
                        lastOrderMillis = System.currentTimeMillis();
                        doLimitCancel();
                        return;
                    }
                }
            }
        }
        ordersController.setPairOrderCurrentPrice(orderCID, currentPrice);
    }*/

    private boolean canBuyForCoinRating() {
        return app.getCoinRatingController() == null || 
                !app.getCoinRatingController().isAlive() || 
                !app.getCoinRatingController().isNoAutoBuysOnDowntrend() || 
                !app.getCoinRatingController().isInDownTrend();
    }

    private void checkStatus() {
        StrategiesController.StrategiesAction saction = strategiesController.checkStatus(
            is_hodling, 
            !isTryingToBuyDip && !buyOnStart && checkOtherStrategies, 
            isTryingToBuyDip ? StrategiesController.StrategiesMode.BUY_DIP : (base_strategy_sell_ignored ? StrategiesController.StrategiesMode.SELL_UP : StrategiesController.StrategiesMode.NORMAL)
        );
        if (saction == StrategiesController.StrategiesAction.DO_ENTER || saction == StrategiesController.StrategiesAction.DO_LEAVE) {
            if (saction == StrategiesController.StrategiesAction.DO_ENTER) {
                if (canBuyForCoinRating()) doEnter(lastStrategyCheckPrice);
            } else {
                doExit(lastStrategyCheckPrice, false);
            }
        }
        info.setLatestPrice(symbol, currentPrice);
        ordersController.updatePairTrade(orderCID);
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
    
    private void tryStartSellUpSeq() {
        Trade last = client.getLastTrade(symbol);
        if (last != null) {
            BigDecimal lastBuyPrice = new BigDecimal(last.getPrice());
            
            BigDecimal currentLimitSellPrice = BigDecimal.ZERO;
            BigDecimal currentLimitSellQty = BigDecimal.ZERO;
            
            CoinBalanceItem qbalance = BalanceController.getInstance().getCoinBalanceInfo(baseAssetSymbol);
            BigDecimal free_cnt = qbalance.getFreeValue();
            BigDecimal order_cnt = BigDecimal.ZERO;
            
            orderToCancelOnSellUp.clear();

            List<Order> openOrders = client.getOpenOrders(symbol);
            if (free_cnt.compareTo(BigDecimal.ZERO)==0 || sellUpAll) {
                for (int i=0; i < openOrders.size(); i++) {
                    if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL) {
                        orderToCancelOnSellUp.add(openOrders.get(i).getOrderId());
                        order_cnt = order_cnt.add(new BigDecimal(openOrders.get(i).getOrigQty()));
                    }
                }
            }
            for (int i=0; i < openOrders.size(); i++) {
                if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL && openOrders.get(i).getType() == OrderType.LIMIT) {
                    currentLimitSellPrice = new BigDecimal(openOrders.get(i).getPrice());
                    currentLimitSellQty = new BigDecimal(openOrders.get(i).getOrigQty());
                }
            }

            if (free_cnt.compareTo(BigDecimal.ZERO) > 0 || (order_cnt.compareTo(BigDecimal.ZERO) > 0 && !orderToCancelOnSellUp.isEmpty())) {
                BigDecimal res_cnt = free_cnt.add(order_cnt);
                inAPIOrder = false;
                base_strategy_sell_ignored = true;
                this.lastBuyPrice = lastBuyPrice;
                lastBuyAmount = res_cnt;
                stopAfterSell = true;
                app.log("START WAITING to sell " + NumberFormatter.df8.format(lastBuyAmount) + " " + baseAssetSymbol + " for price more than " + NumberFormatter.df8.format(lastBuyPrice) + " " + quoteAssetSymbol, true, true);
                boolean result = ordersController.PreBuySell(orderCID, lastBuyAmount, lastBuyPrice, currentLimitSellQty, currentLimitSellPrice);
                if (result) {
                    app.log("Successful waiting start!", true, true);
                    is_hodling = true;
                    info.setLatestPrice(symbol, currentPrice);
                    ordersController.updatePairTrade(orderCID);
                } else {
                    app.log("Error in Buy method!", true, true);
                }
                return;
            }
        }
        isTryingToSellUp = false;
        app.log("Can't set SellUp mode for " + symbol, true, true);
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
            last_time = bars.get(bars.size() - 1).getBeginTime().toInstant().toEpochMilli();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClosePrice().floatValue());
            currentPrice = lastStrategyCheckPrice;
            info.setLatestPrice(symbol, currentPrice);
            ordersController.updatePairTrade(orderCID);
        }

        if (predictor != null && predictor.isHaveNetworkInFile()) {
            predictor.initMinMax(series);
        }
        
        socket = client.OnBarUpdateEvent(symbol.toLowerCase(), barInterval, (nbar, is_fin) -> {
            addBars(Arrays.asList(nbar));
            if (last_time < nbar.getBeginTime().toInstant().toEpochMilli()) {
                last_time = nbar.getBeginTime().toInstant().toEpochMilli();
            }
            currentPrice = new BigDecimal(nbar.getClosePrice().floatValue());
            info.setLatestPrice(symbol, currentPrice);
            ordersController.updatePairTrade(orderCID);
            if (is_fin && (predictor == null || !predictor.isLearning())) {
                app.log(symbol + " current price = " + NumberFormatter.df8.format(currentPrice));
            }
        });
    }

    private void nextBars() {
        List<Bar> bars = null;
        if (need_bar_reset) {
            resetSeries();
            if (init_signal != null) {
                setMainStrategy("Signal");
                strategiesController.startSignal(init_signal);
            }
            strategiesController.resetStrategies();
        } else {
            bars = client.getBars(symbol, barInterval, 500, last_time, null);
            addBars(bars);
        }
        if (bars != null && bars.size() > 0) {
            last_time = bars.get(bars.size() - 1).getBeginTime().toInstant().toEpochMilli();
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
                app.log("Buying...!", true, true);
                is_hodling = true;
            } else {
                app.log("Selling...", true, true);
                is_hodling = false;
                orderToCancelOnSellUp.clear();
                isTryingToSellUp = false;
            }
        }
        if (!isFinished) {
            return;
        }

        inAPIOrder = false;
        
        if (isCancelled || executedQty.compareTo(qty) < 0) {
            // just cancelled or partially filled and then cancelled
            if(isBuying) {
                isTryingToSellUp = false;
                is_hodling = false;
                orderToCancelOnSellUp.clear();
                base_strategy_sell_ignored = false;
            } else {
                is_hodling = true;
            }

            if (executedQty.compareTo(BigDecimal.ZERO) <= 0) {
                app.log("Order for "+(isBuying?"buy":"sell")+" "+symbol+" is cancelled!", true, true);
                ordersController.finishOrder(orderCID, false, price);
            } else {
                if(isBuying) {
                    lastBuyPrice = price;
                    lastBuyAmount = executedQty;
                } else {
                    lastBuyAmount = lastBuyAmount.subtract(executedQty);
                }
                ordersController.finishOrderPart(orderCID, price, executedQty);
                ordersController.finishOrder(orderCID, false, price);
                app.log("Order for "+(isBuying?"buy":"sell")+" "+symbol+" is partially finished! Price = " + NumberFormatter.df8.format(price) + "; Quantity = " + NumberFormatter.df8.format(executedQty), true, true);
            }

        } else {
            // filled
            
            String profitStr = "";
            
            if(isBuying) {
                lastBuyPrice = price;
                lastBuyAmount = executedQty;
                strategiesController.getTradingRecord().enter(series.getBarCount()-1, Decimal.valueOf(price), Decimal.valueOf(executedQty));
            } else {
                strategiesController.getTradingRecord().exit(series.getBarCount()-1, Decimal.valueOf(price), Decimal.valueOf(executedQty));
                fullOrdersCount++;
                
                BigDecimal incomeWithoutComission = price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(client.getTradeComissionPercent()))).subtract(lastBuyPrice);
                BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(lastBuyPrice, RoundingMode.HALF_DOWN);
                last_trade_profit = incomeWithoutComissionPercent;
                profitStr = "; Profit = " + NumberFormatter.df4.format(incomeWithoutComissionPercent) + "%";
            }

            lastOrderMillis = System.currentTimeMillis();

            ordersController.finishOrder(orderCID, true, price);
            app.log("Order for "+(isBuying?"buy":"sell")+" "+symbol+" is finished! Price = "+NumberFormatter.df8.format(price) + profitStr, true, true);
            if (stopAfterSell && !isBuying) {
                doStop();
            }
        }
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

        orderCID = ordersController.registerPairTrade(symbol, this, this::onOrderEvent);

        if (!buyOnStart) {
            doWait(startDelayTime);
        }
        
        resetSeries();
        if (init_signal != null) {
            setMainStrategy("Signal");
            strategiesController.startSignal(init_signal);
        }
        strategiesController.resetStrategies();
        
        if (isTryingToSellUp) {
            tryStartSellUpSeq();
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
    public boolean isHodling() {
        return is_hodling;
    }

    @Override
    public void doBuy() {
        if (!is_hodling && !inAPIOrder) {
            doEnter(currentPrice);
        }
    }

    @Override
    public void doSell() {
        if (is_hodling && !inAPIOrder) {
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
    
    public void doShowStatistics() {
        strategiesController.logStatistics(false);
    }
    
    /**
     * @return the isTryingToSellUp
     */
    public boolean isTryingToSellUp() {
        return isTryingToSellUp;
    }

    /**
     * @param isTryingToSellUp the isTryingToSellUp to set
     */
    public void setTryingToSellUp(boolean isTryingToSellUp) {
        this.isTryingToSellUp = isTryingToSellUp;
    }

    /**
     * @return the isTryingToBuyDip
     */
    public boolean isTryingToBuyDip() {
        return isTryingToBuyDip;
    }

    /**
     * @param isTryingToBuyDip the isTryingToBuyDip to set
     */
    public void setTryingToBuyDip(boolean isTryingToBuyDip) {
        this.isTryingToBuyDip = isTryingToBuyDip;
    }

    /**
     * @return the sellUpAll
     */
    public boolean isSellUpAll() {
        return sellUpAll;
    }

    /**
     * @param sellUpAll the sellUpAll to set
     */
    public void setSellUpAll(boolean sellUpAll) {
        this.sellUpAll = sellUpAll;
    }

    /**
     * @return the do_remove_flag
     */
    public boolean is_do_remove_flag() {
        return do_remove_flag;
    }

    /**
     * @param do_remove_flag the do_remove_flag to set
     */
    public void set_do_remove_flag(boolean do_remove_flag) {
        this.do_remove_flag = do_remove_flag;
    }

    /**
     * @return the tradingBalancePercent
     */
    public BigDecimal getTradingBalancePercent() {
        return tradingBalancePercent;
    }

    /**
     * @param tradingBalancePercent the tradingBalancePercent to set
     */
    public void setTradingBalancePercent(int tradingBalancePercent) {
        this.tradingBalancePercent = new BigDecimal(tradingBalancePercent);
    }

    /**
     * @return the mainStrategy
     */
    public String getMainStrategy() {
        return strategiesController.getMainStrategy();
    }

    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        if (init_signal != null) {
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
        return init_signal;
    }
    
    public void setSignalOrder(SignalItem item) {
        init_signal = item;
    }

    /**
     * @return the orderCID
     */
    public Long getOrderCID() {
        return orderCID;
    }
}
