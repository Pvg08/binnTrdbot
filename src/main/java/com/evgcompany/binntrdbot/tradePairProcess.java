/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;


/**
 *
 * @author EVG_adm_T
 */
public class tradePairProcess extends Thread {
    private static final Semaphore SEMAPHORE_ADD = new Semaphore(1, true);
    
    BinanceApiWebSocketClient client_s = null;
    BinanceApiRestClient client;
    private String symbol;
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    private tradeProfitsController profitsChecker = null;
    private StrategiesController strategiesController = null;
    
    private mainApplication app;
    private boolean need_stop = false;
    private boolean paused = false;
    
    private boolean isTryingToSellUp = false;
    private boolean isTryingToBuyDip = false;
    private boolean sellUpAll = false;
    private boolean lowHold = true;
    private boolean checkOtherStrategies = true;
    
    private List<Long> orderToCancelOnSellUp = new ArrayList<>();
    private long last_time = 0;
    
    private BigDecimal quoteStartBalance = BigDecimal.ZERO;
    
    private long limitOrderId = 0;
    
    private BigDecimal tradingBalancePercent = new BigDecimal("50");
    private BigDecimal tradeMinProfitPercent = new BigDecimal("0.03");
    
    private TimeSeries series = null;
    
    private boolean is_hodling = false;
    private BigDecimal sold_price = BigDecimal.ZERO;
    private BigDecimal sold_amount = BigDecimal.ZERO;
    
    private long delayTime = 10;
    
    private CurrencyPlot plot = null;
    
    private CandlestickInterval barInterval;
    private int barSeconds;
    private int barQueryCount = 500;
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
    
    private long currentPriceMillis = 0;
    private long lastStrategyPriceMillis = 0;
    private BigDecimal currentPrice = BigDecimal.ZERO;
    private BigDecimal lastStrategyCheckPrice = BigDecimal.ZERO;
    
    public tradePairProcess(mainApplication application, BinanceApiRestClient rclient, tradeProfitsController rprofitsChecker, String pair) {
        app = application;
        symbol = pair;
        client = rclient;
        profitsChecker = rprofitsChecker;
        strategiesController = new StrategiesController(symbol, app, profitsChecker);
        setBarInterval("1m");
    }
    
    private void doEnter(BigDecimal curPrice) {
        BigDecimal summ_to_buy = quoteStartBalance.multiply(tradingBalancePercent.multiply(BigDecimal.valueOf(0.01)));
        sold_price = normalizePrice(curPrice);
        sold_amount = normalizeQuantity(summ_to_buy.divide(curPrice, RoundingMode.HALF_DOWN), true);
        sold_amount = normalizeNotionalQuantity(sold_amount, curPrice);
        summ_to_buy = sold_price.multiply(sold_amount);
        base_strategy_sell_ignored = false;
        if (profitsChecker.canBuy(symbol, sold_amount, curPrice)) {
            app.log("BYING " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(summ_to_buy) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")\n", true, true);
            long result = profitsChecker.Buy(symbol, sold_amount, curPrice, false);
            if (result >= 0) {
                limitOrderId = result;
                app.log("Successful!\n", true, true);
                is_hodling = true;
            } else {
                app.log("Error!\n", true, true);
            }
        } else {
            app.log("Can't buy " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(summ_to_buy) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")\n");
        }
    }
    private void doExit(BigDecimal curPrice, boolean skip_check) {
        BigDecimal new_sold_price = sold_amount.multiply(curPrice);
        BigDecimal incomeWithoutComission = sold_amount.multiply(curPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01).multiply(profitsChecker.getTradeComissionPercent()))).subtract(sold_price));
        BigDecimal incomeWithoutComissionPercent = BigDecimal.valueOf(100).multiply(incomeWithoutComission).divide(sold_price.multiply(sold_amount), RoundingMode.HALF_DOWN);
        if (skip_check || !lowHold || incomeWithoutComissionPercent.compareTo(tradeMinProfitPercent) > 0) {
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
                } else {
                    app.log("Error!\n", true, true);
                }
            } else {
                app.log("Can't sell " + sold_amount + " " + symbol + "\n", false, true);
            }
        } else {
            app.log(symbol + " - need to exit but profit ("+incomeWithoutComissionPercent+"%) is too low. Waiting...\n", false, true);
            base_strategy_sell_ignored = true;
        }
    }
    
    private void checkOrder() {
        Order order = client.getOrderStatus(new OrderStatusRequest(symbol, limitOrderId));
        if (order != null && (
                order.getStatus() == OrderStatus.FILLED || 
                order.getStatus() == OrderStatus.CANCELED ||
                order.getStatus() == OrderStatus.EXPIRED ||
                order.getStatus() == OrderStatus.REJECTED
            )
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
            }
            profitsChecker.finishOrder(symbol, order.getStatus() == OrderStatus.FILLED, sold_price);
            profitsChecker.updateAllBalances(true);
            app.log("Limit order for "+order.getSide().name().toLowerCase()+" "+symbol+" is finished! Status="+order.getStatus().name()+"; Price = "+order.getPrice(), true, true);
        }
        profitsChecker.setPairPrice(symbol, currentPrice);
    }
    
    private void checkStatus() {
        StrategiesAction saction = strategiesController.checkStatus(
            is_hodling, 
            !isTryingToBuyDip && !buyOnStart && checkOtherStrategies, 
            isTryingToBuyDip ? StrategiesMode.BUY_DIP : (base_strategy_sell_ignored ? StrategiesMode.SELL_UP : StrategiesMode.NORMAL)
        );
        if (saction == StrategiesAction.DO_ENTER || saction == StrategiesAction.DO_LEAVE) {
            if (saction == StrategiesAction.DO_ENTER) {
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
    
    private void tradeResponse(CandlestickEvent response) {
        app.log(symbol + " = " + response.getClose(), false, true);
    }
    
    private void doWait(long ms) {
        try { Thread.sleep(ms);} catch(InterruptedException e) {}
    }
    
    private void addBar(Candlestick nbar) {
        addBars(Arrays.asList(nbar));
    }
    
    private void addBars(List<Candlestick> nbars) {
        try {
            SEMAPHORE_ADD.acquire();
        } catch (InterruptedException ex) {
            Logger.getLogger(tradePairProcess.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (nbars.size() > 0) {
            ZonedDateTime lastEndTime = null;
            if (series.getBarCount() > 0) {
                long last_end_time = series.getLastBar().getEndTime().toInstant().toEpochMilli();
                lastEndTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(last_end_time), ZoneId.systemDefault());
                
                //System.out.println(last_end_time);
                //System.out.println(lastEndTime);
            }
            
            //System.out.println(lastEndTime);
            
            for(int i=0; i<nbars.size(); i++) {
                
                //System.out.println(i);
                //System.out.println(nbars.get(i));
                
                Candlestick stick = nbars.get(i);
                Duration barDuration = Duration.ofSeconds(barSeconds);
                ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(stick.getCloseTime()), ZoneId.systemDefault());
                Bar newbar = new BaseBar(barDuration, endTime, Decimal.valueOf(stick.getOpen()), Decimal.valueOf(stick.getHigh()), Decimal.valueOf(stick.getLow()), Decimal.valueOf(stick.getClose()), Decimal.valueOf(stick.getVolume()), Decimal.valueOf(stick.getQuoteAssetVolume()));
                boolean updated = false;
                if (lastEndTime != null) {
                    //System.out.println(lastEndTime + "   ***   " + endTime);
                    if (lastEndTime.equals(endTime)) {
                        List<Bar> clist = series.getBarData();
                        clist.set(clist.size()-1, newbar);
                        updated = true;
                        //System.out.println("last bar Updated!");
                    } else if (lastEndTime.isAfter(endTime)) {
                        updated = true;
                        //System.out.println("bad bar!");
                    }
                }
                if (!updated) {
                    series.addBar(newbar);
                    //System.out.println("new bar added!");
                }
                //System.out.println(newbar);
            }
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
        if (filterNotional) {
            if (quantity.multiply(price).compareTo(filterMinNotional) < 0) {
                quantity = filterMinNotional.divide(price);
                quantity = normalizeQuantity(quantity, false);
            }
        }
        return quantity;
    }
    
    private void tryStartSellUpSeq() {
        int i, imax = -1;
        long max_buy_time = 0;
        
        List<Trade> myTrades = client.getMyTrades(symbol);
        for (i=0; i < myTrades.size(); i++) {
            if (myTrades.get(i).isBuyer() && max_buy_time < myTrades.get(i).getTime()) {
                max_buy_time = myTrades.get(i).getTime();
                imax = i;
            }
        }
        if (imax >= 0) {
            BigDecimal lastBuyPrice = new BigDecimal(myTrades.get(imax).getPrice());
            BigDecimal lastBuyQty = new BigDecimal(myTrades.get(imax).getQty());
            
            AssetBalance qbalance = profitsChecker.getAccount().getAssetBalance(baseAssetSymbol);
            BigDecimal free_cnt = new BigDecimal(qbalance.getFree());
            BigDecimal order_cnt = BigDecimal.ZERO;
            
            orderToCancelOnSellUp.clear();
            
            if (free_cnt.compareTo(BigDecimal.ZERO)==0 || sellUpAll) {
                List<Order> openOrders = client.getOpenOrders(new OrderRequest(symbol));
                for (i=0; i < openOrders.size(); i++) {
                    if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL) {
                        orderToCancelOnSellUp.add(openOrders.get(i).getOrderId());
                        order_cnt = order_cnt.add(new BigDecimal(openOrders.get(i).getOrigQty()));
                    }
                }
            }
            
            if (free_cnt.compareTo(BigDecimal.ZERO) > 0 || (order_cnt.compareTo(BigDecimal.ZERO) > 0 && !orderToCancelOnSellUp.isEmpty())) {
                BigDecimal res_cnt = free_cnt.add(order_cnt);
                base_strategy_sell_ignored = true;
                sold_price = lastBuyPrice;
                sold_amount = res_cnt;
                app.log("START WAITING to sell " + df8.format(sold_amount) + " " + baseAssetSymbol + " for price more than " + df8.format(lastBuyPrice) + " " + quoteAssetSymbol, true, true);
                long result = profitsChecker.Buy(symbol, sold_amount, lastBuyPrice, true);
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
            List<Candlestick> bars_pre = client.getCandlestickBars(symbol, barInterval, size, lastbar_from - period + barSeconds * 1000 * 30, lastbar_from + barSeconds * 1000 * 30);
            if (bars_pre.size() > 0) {
                addPreBars(count-1, bars_pre.size(), bars_pre.get(0).getOpenTime(), bars_pre.get(bars_pre.size()-1).getCloseTime());
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
        if (client_s != null) {
            client_s.close();
            client_s = null;
        }
    }
    
    private void resetSeries() {
        series = strategiesController.resetSeries();
        need_bar_reset = false;
        
        stopSockets();
        
        //long ctime = SyncedTime.getInstance(-1).currentTimeMillis();
        //List<Candlestick> bars = client.getCandlestickBars(symbol, barInterval, barQueryCount * 2, ctime - barQueryCount * barSeconds * 1000, ctime);
        List<Candlestick> bars = client.getCandlestickBars(symbol, barInterval);
        if (bars.size() >= 2 && barQueryCount > 1) {
            addPreBars(barQueryCount-1, bars.size(), bars.get(0).getOpenTime(), bars.get(bars.size()-1).getCloseTime());
        }
        if (bars.size() > 0) {
            addBars(bars);
            last_time = bars.get(bars.size() - 1).getOpenTime();
            lastStrategyPriceMillis = System.currentTimeMillis();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClose());
            currentPriceMillis = lastStrategyPriceMillis;
            currentPrice = lastStrategyCheckPrice;
        }
        
        client_s = BinanceApiClientFactory.newInstance().newWebSocketClient();
        socket = client_s.onCandlestickEvent(symbol.toLowerCase(), barInterval, response -> {
            if (socket != null) {
                Candlestick nbar = new Candlestick();
                nbar.setClose(response.getClose());
                nbar.setCloseTime(response.getCloseTime());
                nbar.setHigh(response.getHigh());
                nbar.setLow(response.getLow());
                nbar.setNumberOfTrades(response.getNumberOfTrades());
                nbar.setOpen(response.getOpen());
                nbar.setOpenTime(response.getOpenTime());
                nbar.setQuoteAssetVolume(response.getQuoteAssetVolume());
                nbar.setTakerBuyBaseAssetVolume(response.getTakerBuyBaseAssetVolume());
                nbar.setTakerBuyQuoteAssetVolume(response.getTakerBuyQuoteAssetVolume());
                nbar.setVolume(response.getVolume());
                addBar(nbar);
                if (last_time < nbar.getOpenTime()) {
                    last_time = nbar.getOpenTime();
                }
                currentPriceMillis = System.currentTimeMillis();
                currentPrice = new BigDecimal(response.getClose());
                if (profitsChecker != null) {
                    profitsChecker.setPairPrice(symbol, currentPrice);
                }
            }
        });
    }
    private void nextBars() {
        List<Candlestick> bars = null;
        if (need_bar_reset) {
            resetSeries();
            strategiesController.resetStrategies();
        } else {
            bars = client.getCandlestickBars(symbol, barInterval, 500, last_time, last_time + barSeconds * 2000);
            addBars(bars);
        }
        if (bars != null && bars.size() > 0) {
            last_time = bars.get(bars.size() - 1).getOpenTime();
            //app.log(symbol + ": price=" + bars.get(bars.size() - 1).getClose() + "; volume=" + bars.get(bars.size() - 1).getVolume() + "; bars=" + series.getBarCount(), false, true);
            lastStrategyPriceMillis = System.currentTimeMillis();
            lastStrategyCheckPrice = new BigDecimal(bars.get(bars.size() - 1).getClose());
            currentPriceMillis = lastStrategyPriceMillis;
            currentPrice = lastStrategyCheckPrice;
        }
    }
    
    @Override
    public void run(){  
        int exceptions_cnt = 0;

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
        
        // https://github.com/ta4j/ta4j/blob/master/ta4j-examples/src/main/java/ta4jexamples/loaders/CsvTradesLoader.java
        // https://github.com/ta4j/ta4j/blob/master/ta4j-examples/src/main/java/ta4jexamples/bots/TradingBotOnMovingTimeSeries.java
        
        // autoselect strategy
        // https://github.com/mdeverdelhan/ta4j-origins/blob/master/ta4j-examples/src/main/java/ta4jexamples/walkforward/WalkForward.java
        
        
        resetSeries();

        /*
        System.out.println(bars.get(bars.size()-1));
        */
        
        
        /*if (bars.size() > 0) {
            app.log(symbol + ": price=" + bars.get(bars.size() - 1).getClose() + "; volume=" + bars.get(bars.size() - 1).getVolume() + "; bars=" + series.getBarCount(), false, true);
        }*/
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
        
        while (!need_stop) {
            if (paused) {
                doWait(12000);
                continue;
            }
            try { 
                nextBars();
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
        if (limitOrderId > 0) {
            client.cancelOrder(new CancelOrderRequest(symbol, limitOrderId));
            doWait(1000);
            checkOrder();
        }
    }
    
    public void doShowPlot() {
        plot = new CurrencyPlot(symbol, series, barSeconds);
        for(int i=0; i<strategiesController.getStrategyMarkers().size(); i++) {
            plot.addMarker(strategiesController.getStrategyMarkers().get(i).label, strategiesController.getStrategyMarkers().get(i).timeStamp, strategiesController.getStrategyMarkers().get(i).typeIndex);
        }
        plot.showPlot();
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
    }
    
    /**
     * @param _barInterval the barInterval to set
     */
    public void setBarInterval(String _barInterval) {
        int newSeconds = barSeconds;
        if ("1m".equals(_barInterval)) {
            barInterval = CandlestickInterval.ONE_MINUTE;
            newSeconds = 60;
            barQueryCount = 5;
        } else if ("5m".equals(_barInterval)) {
            barInterval = CandlestickInterval.FIVE_MINUTES;
            newSeconds = 60 * 5;
            barQueryCount = 4;
        } else if ("15m".equals(_barInterval)) {
            barInterval = CandlestickInterval.FIFTEEN_MINUTES;
            newSeconds = 60 * 15;
            barQueryCount = 3;
        } else if ("30m".equals(_barInterval)) {
            barInterval = CandlestickInterval.HALF_HOURLY;
            newSeconds = 60 * 30;
            barQueryCount = 2;
        } else if ("1h".equals(_barInterval)) {
            barInterval = CandlestickInterval.HOURLY;
            newSeconds = 60 * 60;
            barQueryCount = 2;
        } else if ("2h".equals(_barInterval)) {
            barInterval = CandlestickInterval.TWO_HOURLY;
            newSeconds = 60 * 120;
            barQueryCount = 2;
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

    /**
     * @return the lowHold
     */
    public boolean isLowHold() {
        return lowHold;
    }

    /**
     * @param lowHold the lowHold to set
     */
    public void setLowHold(boolean lowHold) {
        this.lowHold = lowHold;
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
}