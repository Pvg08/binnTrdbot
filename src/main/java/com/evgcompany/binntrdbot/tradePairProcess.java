/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.exception.BinanceApiException;
import com.evgcompany.binntrdbot.analysis.NeuralNetworkStockPredictor;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
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
public class tradePairProcess extends Thread {
    private static final Semaphore SEMAPHORE_ADD = new Semaphore(1, true);
    
    TradingAPIAbstractInterface client;
    
    NeuralNetworkStockPredictor predictor = null;
    
    private final String symbol;
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    private tradeProfitsController profitsChecker = null;
    private StrategiesController strategiesController = null;
    
    private final mainApplication app;
    private boolean need_stop = false;
    private boolean paused = false;
    
    private boolean isTryingToSellUp = false;
    private boolean isTryingToBuyDip = false;
    private boolean stopAfterSell = false;
    private boolean sellUpAll = false;
    private boolean checkOtherStrategies = true;
    
    private List<Long> orderToCancelOnSellUp = new ArrayList<>();
    private long last_time = 0;
    
    private BigDecimal quoteStartBalance = BigDecimal.ZERO;
    
    private long limitOrderId = 0;
    
    private BigDecimal tradingBalancePercent = new BigDecimal("50");
    
    private TimeSeries series = null;
    
    private boolean is_hodling = false;
    private BigDecimal sold_price = BigDecimal.ZERO;
    private BigDecimal sold_amount = BigDecimal.ZERO;
    
    private long delayTime = 10;
    
    private CurrencyPlot plot = null;
    
    private String barInterval;
    private int barSeconds;
    private int barQueryCount = 1;
    private boolean need_bar_reset = false;
    private Closeable socket = null;
    
    private boolean buyOnStart = false;
    
    private boolean base_strategy_sell_ignored = false;
    private boolean do_remove_flag = false;
    
    private boolean filterPrice = false;
    private BigDecimal filterPriceTickSize = BigDecimal.ZERO;
    private BigDecimal filterMinPrice = BigDecimal.ZERO;
    private BigDecimal filterMaxPrice = BigDecimal.ZERO;
    private boolean filterQty = false;
    private BigDecimal filterQtyStep = BigDecimal.ZERO;
    private BigDecimal filterMinQty = BigDecimal.ZERO;
    private BigDecimal filterMaxQty = BigDecimal.ZERO;
    private boolean filterNotional = false;
    private BigDecimal filterMinNotional = BigDecimal.ZERO;
    
    private static DecimalFormat df5 = new DecimalFormat("0.#####");
    private static DecimalFormat df8 = new DecimalFormat("0.########");
    private int startDelayTime = 0;
    
    private boolean useBuyStopLimited = false;
    private int stopBuyLimitTimeout = 120;
    private boolean useSellStopLimited = false;
    private boolean isInitialized = false;
    private int stopSellLimitTimeout = 1200;
    
    private long lastOrderMillis = 0;
    private long lastStrategyPriceMillis = 0;
    private BigDecimal currentPrice = BigDecimal.ZERO;
    private BigDecimal lastStrategyCheckPrice = BigDecimal.ZERO;
    
    public tradePairProcess(mainApplication application, TradingAPIAbstractInterface rclient, tradeProfitsController rprofitsChecker, String pair) {
        app = application;
        symbol = pair;
        client = rclient;
        isInitialized = false;
        profitsChecker = rprofitsChecker;
        strategiesController = new StrategiesController(symbol, app, profitsChecker);
        setBarInterval("1m");
    }

    @Override
    public void finalize() throws Throwable {
        if (socket != null) {
            socket.close();
        }
        super.finalize();
    }
    
    private void doEnter(BigDecimal curPrice) {
        if (curPrice == null || curPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal summ_to_buy = quoteStartBalance.multiply(tradingBalancePercent.divide(BigDecimal.valueOf(100)));
        sold_price = normalizePrice(curPrice);
        sold_amount = normalizeQuantity(summ_to_buy.divide(curPrice, RoundingMode.HALF_DOWN), true);
        sold_amount = normalizeNotionalQuantity(sold_amount, curPrice);
        if (!profitsChecker.canBuy(symbol, sold_amount, curPrice) && filterQtyStep != null && sold_amount.compareTo(filterQtyStep) > 0) {
            sold_amount = sold_amount.subtract(filterQtyStep);
        }
        summ_to_buy = sold_price.multiply(sold_amount);
        base_strategy_sell_ignored = false;
        if (profitsChecker.canBuy(symbol, sold_amount, curPrice)) {
            app.log("BYING " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(summ_to_buy) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")", true, true);
            long result = profitsChecker.Buy(symbol, sold_amount, curPrice);
            if (result >= 0) {
                limitOrderId = result;
                app.log("Successful!", true, true);
                is_hodling = true;
                lastOrderMillis = System.currentTimeMillis();
                if (limitOrderId == 0) {
                    strategiesController.getTradingRecord().enter(series.getBarCount()-1, Decimal.valueOf(curPrice), Decimal.valueOf(sold_amount));
                }
            } else {
                app.log("Error!", true, true);
            }
        } else {
            app.log("Can't buy " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(summ_to_buy) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")");
        }
    }
    private void doExit(BigDecimal curPrice, boolean skip_check) {
        if (curPrice == null || curPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal new_sold_price = sold_amount.multiply(curPrice);
        BigDecimal incomeWithoutComission = sold_amount.multiply(curPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(profitsChecker.getTradeComissionPercent()))).subtract(sold_price));
        BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(sold_price.multiply(sold_amount), RoundingMode.HALF_DOWN);
        if (
                skip_check || 
                !profitsChecker.isLowHold() || 
                incomeWithoutComissionPercent.compareTo(profitsChecker.getTradeMinProfitPercent()) > 0 ||
                (
                    profitsChecker.getStopLossPercent() != null &&
                    incomeWithoutComissionPercent.compareTo(profitsChecker.getStopLossPercent().multiply(BigDecimal.valueOf(-1))) < 0
                )
            ) {
            if (profitsChecker.canSell(symbol, sold_amount)) {
                base_strategy_sell_ignored = false;
                app.log("SELLING " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(new_sold_price) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")", true, true);
                long result = profitsChecker.Sell(symbol, sold_amount, curPrice, orderToCancelOnSellUp);
                if (result >= 0) {
                    limitOrderId = result;
                    app.log("Successful!", true, true);
                    app.log("RESULT: " + df8.format(incomeWithoutComission) + " " + quoteAssetSymbol + " (" + df8.format(incomeWithoutComissionPercent) + "%)\n", true);
                    is_hodling = false;
                    orderToCancelOnSellUp.clear();
                    isTryingToSellUp = false;
                    lastOrderMillis = System.currentTimeMillis();
                    if (limitOrderId == 0) {
                        strategiesController.getTradingRecord().exit(series.getBarCount()-1, Decimal.valueOf(curPrice), Decimal.valueOf(sold_amount));
                        if (stopAfterSell) {
                            doStop();
                        }
                    }
                } else {
                    app.log("Error!", true, true);
                }
            } else {
                app.log("Can't sell " + sold_amount + " " + symbol + "", false, true);
            }
        } else {
            app.log(symbol + " - need to exit but profit ("+incomeWithoutComissionPercent+"%) is too low. Waiting...", false, true);
            base_strategy_sell_ignored = true;
        }
    }
    
    private void checkOrder() {
        if (profitsChecker.isTestMode()) {
            profitsChecker.finishOrder(symbol, true, currentPrice);
            return;
        }
        Order order = client.getOrderStatus(symbol, limitOrderId);
        if(order != null) {
            if (
                order.getStatus() == OrderStatus.FILLED || 
                order.getStatus() == OrderStatus.PARTIALLY_FILLED || 
                order.getStatus() == OrderStatus.CANCELED ||
                order.getStatus() == OrderStatus.EXPIRED ||
                order.getStatus() == OrderStatus.REJECTED
            ) {
                limitOrderId = 0;
                if (order.getStatus() != OrderStatus.FILLED) {
                    if(order.getSide() == OrderSide.BUY) {
                        isTryingToSellUp = false;
                        is_hodling = false;
                        orderToCancelOnSellUp.clear();
                        base_strategy_sell_ignored = false;
                    } else {
                        is_hodling = true;
                    }
                    if (order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
                        profitsChecker.finishOrderPart(symbol, new BigDecimal(order.getPrice()), new BigDecimal(order.getExecutedQty()));
                        sold_amount = sold_amount.subtract(new BigDecimal(order.getExecutedQty()));
                        app.log("Limit order for "+order.getSide().name().toLowerCase()+" "+symbol+" is partially finished! Price = "+order.getPrice() + "; Quantity = " + order.getExecutedQty(), true, true);
                        profitsChecker.updateAllBalances(true);
                        return;
                    }
                } else {
                    if(order.getSide() == OrderSide.BUY) {
                        strategiesController.getTradingRecord().enter(series.getBarCount()-1, Decimal.valueOf(order.getPrice()), Decimal.valueOf(sold_amount));
                    } else {
                        strategiesController.getTradingRecord().exit(series.getBarCount()-1, Decimal.valueOf(order.getPrice()), Decimal.valueOf(sold_amount));
                    }
                }
                profitsChecker.finishOrder(symbol, order.getStatus() == OrderStatus.FILLED, new BigDecimal(order.getPrice()));
                profitsChecker.updateAllBalances(true);
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
        profitsChecker.setPairPrice(symbol, currentPrice);
    }
    
    private void checkStatus() {
        StrategiesController.StrategiesAction saction = strategiesController.checkStatus(
            is_hodling, 
            !isTryingToBuyDip && !buyOnStart && checkOtherStrategies, 
            isTryingToBuyDip ? StrategiesController.StrategiesMode.BUY_DIP : (base_strategy_sell_ignored ? StrategiesController.StrategiesMode.SELL_UP : StrategiesController.StrategiesMode.NORMAL)
        );
        if (saction == StrategiesController.StrategiesAction.DO_ENTER || saction == StrategiesController.StrategiesAction.DO_LEAVE) {
            if (saction == StrategiesController.StrategiesAction.DO_ENTER) {
                doEnter(lastStrategyCheckPrice);
            } else {
                doExit(lastStrategyCheckPrice, false);
            }
        }
        profitsChecker.setPairPrice(symbol, currentPrice);
    }
    
    public void doStop() {
        need_stop = true;
        paused = false;
    }
    
    void doSetPaused(boolean _paused) {
        paused = _paused;
    }
    
    private void doWait(long ms) {
        try { Thread.sleep(ms);} catch(InterruptedException e) {}
    }
    
    private void addBar(Bar nbar) {
        addBars(Arrays.asList(nbar));
    }
    
    private void addBars(List<Bar> nbars) {
        try {
            SEMAPHORE_ADD.acquire();
        } catch (InterruptedException ex) {
            Logger.getLogger(tradePairProcess.class.getName()).log(Level.SEVERE, null, ex);
        }
        client.addUpdateSeriesBars(series, nbars);
        if (plot != null) {
            plot.updateSeries();
        }
        SEMAPHORE_ADD.release();
    }
    
    public BigDecimal normalizeQuantity(BigDecimal qty, boolean qty_down_only) {
        if (filterQty) {
            BigDecimal pqty = qty;
            if (filterQtyStep.compareTo(BigDecimal.ZERO) > 0) {
                qty = qty.divide(filterQtyStep).setScale(0, RoundingMode.HALF_UP).multiply(filterQtyStep);
            }
            if (qty.compareTo(filterMinQty) < 0) {
                qty = filterMinQty;
            } else if (qty.compareTo(filterMaxQty) > 0) {
                qty = filterMaxQty;
            }
            if (qty_down_only && qty.compareTo(pqty) > 0) {
                qty = qty.subtract(filterQtyStep);
                if (qty.compareTo(BigDecimal.ZERO) < 0) {
                    qty = BigDecimal.ZERO;
                }
            }
        }
        return qty;
    }
    public BigDecimal normalizePrice(BigDecimal price) {
        if (filterPrice) {
            if (filterPriceTickSize.compareTo(BigDecimal.ZERO) > 0) {
                price = price.divide(filterPriceTickSize).setScale(0, RoundingMode.HALF_UP).multiply(filterPriceTickSize);
            }
            if (price.compareTo(filterMinPrice) < 0) {
                price = filterMinPrice;
            } else if (price.compareTo(filterMaxPrice) > 0) {
                price = filterMaxPrice;
            }
        }
        return price;
    }
    public BigDecimal normalizeNotionalQuantity(BigDecimal quantity, BigDecimal price) {
        if (filterNotional && price.compareTo(BigDecimal.ZERO) > 0) {
            if (quantity.multiply(price).compareTo(filterMinNotional) < 0) {
                quantity = filterMinNotional.divide(price, filterMinNotional.scale(), RoundingMode.HALF_UP);
                quantity = normalizeQuantity(quantity, false);
            }
        }
        return quantity;
    }
    
    private void tryStartSellUpSeq() {
        Trade last = client.getLastTrade(symbol);
        if (last != null) {
            BigDecimal lastBuyPrice = new BigDecimal(last.getPrice());
            BigDecimal lastBuyQty = new BigDecimal(last.getQty());
            
            BigDecimal currentLimitSellPrice = BigDecimal.ZERO;
            BigDecimal currentLimitSellQty = BigDecimal.ZERO;
            
            AssetBalance qbalance = profitsChecker.getClient().getAssetBalance(baseAssetSymbol);
            BigDecimal free_cnt = new BigDecimal(qbalance.getFree());
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
                base_strategy_sell_ignored = true;
                sold_price = lastBuyPrice;
                sold_amount = res_cnt;
                stopAfterSell = true;
                app.log("START WAITING to sell " + df8.format(sold_amount) + " " + baseAssetSymbol + " for price more than " + df8.format(lastBuyPrice) + " " + quoteAssetSymbol, true, true);
                long result = profitsChecker.PreBuySell(symbol, sold_amount, lastBuyPrice, currentLimitSellQty, currentLimitSellPrice);
                if (result >= 0) {
                    limitOrderId = result;
                    app.log("Successful waiting start!", true, true);
                    is_hodling = true;
                    profitsChecker.setPairPrice(symbol, currentPrice);
                } else {
                    app.log("Error in Buy method!", true, true);
                }
                return;
            }
        }
        isTryingToSellUp = false;
        app.log("Can't set SellUp mode for " + symbol, true, true);
    } 
    
    private void addPreBars(int count, int size, long lastbar_from, long lastbar_to) {
        if (count > 0) {
            long period = Math.floorDiv(lastbar_to - lastbar_from + 1, 1000) * 1000;
            List<Bar> bars_pre = client.getBars(symbol, barInterval, size, lastbar_from - period + barSeconds * 1000 * 30, lastbar_from + barSeconds * 1000 * 30);
            if (bars_pre.size() > 0) {
                addPreBars(count-1, bars_pre.size(), bars_pre.get(0).getBeginTime().toInstant().toEpochMilli(), bars_pre.get(bars_pre.size()-1).getEndTime().toInstant().toEpochMilli());
                addBars(bars_pre);
            }
        }
    }
    
    private void stopSockets() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(tradePairProcess.class.getName()).log(Level.SEVERE, null, ex);
            }
            socket = null;
        }
    }

    private void resetSeries() {
        series = strategiesController.resetSeries();
        
        if (strategiesController.getMainStrategy().equals("Neural Network")) {
            predictor = new NeuralNetworkStockPredictor(symbol, 200);
            if (!predictor.isHaveNetworkInFile() && predictor.isHaveNetworkInBaseFile()) {
                predictor.toBase();
            }
            predictor.setLearningRate(0.9);
        }
        
        need_bar_reset = false;
        
        stopSockets();
        
        //long ctime = SyncedTime.getInstance(-1).currentTimeMillis();
        //List<Candlestick> bars = client.getCandlestickBars(symbol, barInterval, barQueryCount * 2, ctime - barQueryCount * barSeconds * 1000, ctime);
        List<Bar> bars = client.getBars(symbol, barInterval);
        if (bars.size() >= 2 && barQueryCount > 1) {
            addPreBars(barQueryCount-1, bars.size(), bars.get(0).getBeginTime().toInstant().toEpochMilli(), bars.get(bars.size()-1).getEndTime().toInstant().toEpochMilli());
        }
        if (bars.size() > 0) {
            addBars(bars);
            last_time = bars.get(bars.size() - 1).getBeginTime().toInstant().toEpochMilli();
            lastStrategyPriceMillis = System.currentTimeMillis();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClosePrice().floatValue());
            currentPrice = lastStrategyCheckPrice;
        }

        if (predictor != null) {
            if (!predictor.isHaveNetworkInFile()) {
                predictor.setSaveTrainData(true);
                if (!predictor.isHaveDatasetInFile()) predictor.initTrainNetwork(series);
                predictor.start();
            } else {
                predictor.initMinMax(series);
            }
        }
        
        socket = client.OnBarUpdateEvent(symbol.toLowerCase(), barInterval, (nbar, is_fin) -> {
            addBar(nbar);
            if (last_time < nbar.getBeginTime().toInstant().toEpochMilli()) {
                last_time = nbar.getBeginTime().toInstant().toEpochMilli();
            }
            currentPrice = new BigDecimal(nbar.getClosePrice().floatValue());
            if (profitsChecker != null) {
                profitsChecker.setPairPrice(symbol, currentPrice);
            }
            if (is_fin) {
                app.log(symbol + " current price = " + df8.format(currentPrice));
            }
        });
    }

    private void nextBars() {
        List<Bar> bars = null;
        if (need_bar_reset) {
            resetSeries();
            strategiesController.resetStrategies();
        } else {
            bars = client.getBars(symbol, barInterval, 500, last_time, last_time + barSeconds * 2000);
            addBars(bars);
        }
        if (bars != null && bars.size() > 0) {
            last_time = bars.get(bars.size() - 1).getBeginTime().toInstant().toEpochMilli();
            //app.log(symbol + ": price=" + bars.get(bars.size() - 1).getClose() + "; volume=" + bars.get(bars.size() - 1).getVolume() + "; bars=" + series.getBarCount(), false, true);
            lastStrategyPriceMillis = System.currentTimeMillis();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClosePrice().floatValue());
            currentPrice = lastStrategyCheckPrice;
        }
    }
    
    public void doNetworkAction(String train, String base) {
        if (strategiesController.getMainStrategy().equals("Neural Network")) {
            predictor = new NeuralNetworkStockPredictor(base.equals("COIN") ? symbol : "", 200);
            predictor.setLearningRate(0.9);
            if (train.equals("TRAIN")) {
                predictor.start();
            } else if (train.equals("ADDSET")) {
                predictor.setSaveTrainData(true);
                predictor.loadTrainingData(series);
            }
        }
    }
    
    @Override
    public void run(){  
        int exceptions_cnt = 0;
        isInitialized = false;
        limitOrderId = 0;

        if (!buyOnStart) {
            doWait(ThreadLocalRandom.current().nextLong(100, 500));
        }

        app.log("thread for " + symbol + " running...");
        app.log("");
        
        if (!buyOnStart) {
            doWait(startDelayTime);
        }
        
        try {
            ExchangeInfo info = client.getExchangeInfo();
            SymbolInfo pair_sinfo = info.getSymbolInfo(symbol);
            baseAssetSymbol = pair_sinfo.getBaseAsset();
            quoteAssetSymbol = pair_sinfo.getQuoteAsset();

            List<SymbolFilter> filters = pair_sinfo.getFilters();        
            filters.forEach((filter)->{
                if (null != filter.getFilterType()) switch (filter.getFilterType()) {
                    case PRICE_FILTER:
                        filterPrice = true;
                        filterMinPrice = new BigDecimal(filter.getMinPrice());
                        filterMaxPrice = new BigDecimal(filter.getMaxPrice());
                        filterPriceTickSize = new BigDecimal(filter.getTickSize());
                        break;
                    case LOT_SIZE:
                        filterQty = true;
                        filterQtyStep = new BigDecimal(filter.getStepSize());
                        filterMinQty = new BigDecimal(filter.getMinQty());
                        filterMaxQty = new BigDecimal(filter.getMaxQty());
                        break;
                    case MIN_NOTIONAL:
                        filterNotional = true;
                        filterMinNotional = new BigDecimal(filter.getMinNotional());
                        break;
                    default:
                        break;
                }
            });
        } catch (Exception e) {
            app.log("symbol " + symbol + " error. Exiting from thread...");
            return;
        }

        profitsChecker.placeOrUpdatePair(baseAssetSymbol, quoteAssetSymbol, symbol, true);

        app.log(symbol + " filters:");
        if (filterPrice) {
            app.log("Price: min="+df8.format(filterMinPrice)+"; max="+df8.format(filterMaxPrice)+"; tick=" + df8.format(filterPriceTickSize));
        }
        if (filterQty) {
            app.log("Quantity: min="+df8.format(filterMinQty)+"; max="+df8.format(filterMaxQty)+"; step=" + df8.format(filterQtyStep));
        }
        if (filterNotional) {
            app.log("Notional: " + df8.format(filterMinNotional));
        }
        app.log("");

        if (!buyOnStart) {
            doWait(startDelayTime);
        }
        
        resetSeries();

        strategiesController.resetStrategies();
        
        if (isTryingToSellUp) {
            tryStartSellUpSeq();
        }
        
        currencyItem quote = profitsChecker.getProfitData(quoteAssetSymbol);
        if (quote != null) {
            quoteStartBalance = quote.getInitialValue();
        }
        
        if (buyOnStart) {
            doBuy();
        }
        
        isInitialized = true;
        app.log(symbol + " initialized. Current price = " + df8.format(currentPrice));
        
        while (!need_stop) {
            if (paused) {
                doWait(12000);
                continue;
            }
            try { 
                nextBars();
                
                if(strategiesController.getMainStrategy().equals("Neural Network")) {
                    if (predictor != null && !predictor.isLearning() && predictor.isHaveNetworkInFile()) {
                        double np = predictor.predictNextPrice(series);
                        app.log(symbol + " NEUR prediction = " + df8.format(np));
                    }
                }

                if (limitOrderId > 0) {
                    checkOrder();
                } else {
                    checkStatus();
                }
            } catch(BinanceApiException binex) {
                app.log("");
                app.log("EXCEPTION: " + binex.getLocalizedMessage(), false, true);
                exceptions_cnt++;
                if (exceptions_cnt > 3) {
                    break;
                }
                doWait(delayTime * 10000);
                continue;
            }
            doWait(delayTime * 1000);
            exceptions_cnt = 0;
        }
        
        stopSockets();
        
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

    public void doBuy() {
        if (!is_hodling && limitOrderId == 0) {
            this.doEnter(currentPrice);
        }
    }

    public void doSell() {
        if (is_hodling && limitOrderId == 0) {
            this.doExit(currentPrice, true);
        }
    }

    public void doLimitCancel() {
        
        System.out.println(limitOrderId);
        
        if (limitOrderId > 0) {
            profitsChecker.cancelOrder(symbol, limitOrderId);
            if (profitsChecker.isTestMode()) {
                limitOrderId = 0;
            } else {
                doWait(1000);
                checkOrder();
            }
        }
    }
    
    public void doShowPlot() {
        plot = new CurrencyPlot(symbol, series, barSeconds);
        for(int i=0; i<strategiesController.getStrategyMarkers().size(); i++) {
            plot.addMarker(strategiesController.getStrategyMarkers().get(i).label, strategiesController.getStrategyMarkers().get(i).timeStamp, strategiesController.getStrategyMarkers().get(i).typeIndex);
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
        strategiesController.setMainStrategy(mainStrategy);
        need_bar_reset = true;
        if (predictor != null) {
            if (predictor.getLearningRule() != null) {
                predictor.getLearningRule().stopLearning();
            }
            predictor = null;
        }
    }
    
    /**
     * @param _barInterval the barInterval to set
     */
    public final void setBarInterval(String _barInterval) {
        int newSeconds = barSeconds;
        if (
                "1m".equals(_barInterval) || 
                "5m".equals(_barInterval) || 
                "15m".equals(_barInterval) || 
                "30m".equals(_barInterval) || 
                "1h".equals(_barInterval) || 
                "2h".equals(_barInterval)
            ) {
            barInterval = _barInterval;
            newSeconds = client.barIntervalToSeconds(_barInterval);
        }
        if (newSeconds != barSeconds) {
            barSeconds = newSeconds;
            strategiesController.setBarSeconds(newSeconds);
            if (series != null && series.getBarCount() > 0) {
                need_bar_reset = true;
            }
        }
    }

    /**
     * @return the delayTime
     */
    public long getDelayTime() {
        return delayTime;
    }

    /**
     * @param delayTime the delayTime to set
     */
    public void setDelayTime(long delayTime) {
        this.delayTime = delayTime;
    }

    void setBuyOnStart(boolean buyOnStart) {
        this.buyOnStart = buyOnStart;
    }

    void setStartDelay(int startDelayTime) {
        this.startDelayTime = startDelayTime;
    }

    void setCheckOtherStrategies(boolean checkOtherStrategies) {
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
     * @return the isInitialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    public boolean isInLimitOrder() {
        return limitOrderId>0;
    }
}
