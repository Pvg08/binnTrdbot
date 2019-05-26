/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.api;

import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.RateLimit;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerPrice;
import com.evgcompany.binntrdbot.events.BalanceEvent;
import com.evgcompany.binntrdbot.events.BarEvent;
import com.evgcompany.binntrdbot.events.OrderEvent;
import com.evgcompany.binntrdbot.events.SocketClosedEvent;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import com.fasterxml.jackson.core.JsonParseException;
import java.io.Closeable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_adm_T
 */
public abstract class TradingAPIAbstractInterface {
    
    protected String secret, key;
    protected BigDecimal tradeComissionPercent = new BigDecimal("0.1");
    protected BigDecimal tradeComissionCurrencyPercent = null;
    protected String tradeComissionCurrency = null;
    
    public TradingAPIAbstractInterface(String secret, String key) {
        this.secret = secret;
        this.key = key;
    }
    
    abstract public boolean connect();
    abstract public boolean disconnect();
    
    protected static String numberFormatForOrder(BigDecimal num) {
        return NumberFormatter.df8.format(num).replace(".","").replace(",",".").replace(" ","");
    }
    protected static String numberFormatForOrder(double num) {
        return numberFormatForOrder(BigDecimal.valueOf(num));
    }
    
    public long getServerTime() {
        return System.currentTimeMillis();
    }
    
    public long getAlignedCurrentTimeMillis() {
        return System.currentTimeMillis();
    }
    
    /**
     * @return the tradeComissionPercent
     */
    public BigDecimal getTradeComissionPercent() {
        return tradeComissionPercent;
    }

    /**
     * @return the tradeComissionCurrency
     */
    public String getTradeComissionCurrency() {
        return tradeComissionCurrency;
    }
    
    /**
     * @return the tradeComissionCurrencyPercent
     */
    public BigDecimal getTradeComissionCurrencyPercent() {
        return tradeComissionCurrencyPercent;
    }
    
    public int barIntervalToSeconds(String _barInterval) {
        int newSeconds = 0;
        switch (_barInterval) {
            case "1m":
                newSeconds = 60;
                break;
            case "5m":
                newSeconds = 60 * 5;
                break;
            case "15m":
                newSeconds = 60 * 15;
                break;
            case "30m":
                newSeconds = 60 * 30;
                break;
            case "1h":
                newSeconds = 1 * 60 * 60;
                break;
            case "2h":
                newSeconds = 2 * 60 * 60;
                break;
            case "3h":
                newSeconds = 3 * 60 * 60;
                break;
            case "4h":
                newSeconds = 4 * 60 * 60;
                break;
            case "6h":
                newSeconds = 6 * 60 * 60;
                break;
            case "8h":
                newSeconds = 8 * 60 * 60;
                break;
            case "12h":
                newSeconds = 12 * 60 * 60;
                break;
            case "1d":
                newSeconds = 24 * 60 * 60;
                break;
            default:
                break;
        }
        return newSeconds;
    }
    
    public static void addSeriesBars(TimeSeries series, List<Bar> bars) {
        if (bars != null && !bars.isEmpty()) {
            for(int i=0; i<bars.size(); i++) {
                series.addBar(bars.get(i));
            }
        }
    }
    
    public void addUpdateSeriesBars(TimeSeries series, List<Bar> nbars) {
        if (nbars.size() > 0) {
            ZonedDateTime lastEndTime = null;
            if (series.getBarCount() > 0) {
                long last_end_time = series.getLastBar().getEndTime().toInstant().toEpochMilli();
                lastEndTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(last_end_time), ZoneId.systemDefault());
            }
            for(int i=0; i<nbars.size(); i++) {
                Bar stick = nbars.get(i);
                ZonedDateTime endTime = stick.getEndTime();
                boolean updated = false;
                if (lastEndTime != null) {
                    if (lastEndTime.equals(endTime)) {
                        List<Bar> clist = series.getBarData();
                        clist.set(clist.size()-1, stick);
                        updated = true;
                    } else if (lastEndTime.isAfter(endTime)) {
                        updated = true;
                    }
                }
                if (!updated) {
                    series.addBar(stick);
                }
            }
        }
    }
    
    private void mergeBars(List<Bar> bars, List<Bar> bars_next) {
        if (bars.isEmpty()) {
            if (!bars_next.isEmpty()) {
                bars.addAll(bars_next);
            }
            return;
        }
        long last_end_time = bars.get(bars.size()-1).getEndTime().toInstant().toEpochMilli();
        int i = 0;
        while (i < bars_next.size() && 
                bars_next.get(i).getBeginTime().toInstant().toEpochMilli() < last_end_time
        ) {
            i++;
        }
        for(;i<bars_next.size();i++) {
            bars.add(bars_next.get(i));
        }
    }
    
    private List<Bar> getPreBars(String pair, String barInterval, int n, int size, long lastbar_from, long lastbar_to) {
        List<Bar> result = new ArrayList<>();
        if (n > 0) {
            long period = Math.floorDiv(lastbar_to - lastbar_from + 1, 1000) * 1000;
            long barSeconds = barIntervalToSeconds(barInterval);
            result = getBars(pair, barInterval, size, lastbar_from - period + barSeconds * 1000 * 30, lastbar_from + barSeconds * 1000 * 30);
            if (result.size() > 0) {
                List<Bar> pre = getPreBars(pair, barInterval, n-1, result.size(), result.get(0).getBeginTime().toInstant().toEpochMilli(), result.get(result.size()-1).getEndTime().toInstant().toEpochMilli());
                mergeBars(pre, result);
                result = pre;
            }
        }
        return result;
    }
    
    public List<Bar> getBars(String pair, String bar_interval, Integer count, int queries_cnt) {
        List<Bar> result = getBars(pair, bar_interval, count, null, null);
        if (result.size() >= 2 && queries_cnt > 1) {
            List<Bar> pre = getPreBars(pair, bar_interval, queries_cnt-1, result.size(), result.get(0).getBeginTime().toInstant().toEpochMilli(), result.get(result.size()-1).getEndTime().toInstant().toEpochMilli());
            mergeBars(pre, result);
            result = pre;
        }
        return result;
    }
    
    abstract public List<RateLimit> getRateLimits();
    abstract public List<Bar> getBars(String pair, String bar_interval);
    abstract public List<Bar> getBars(String pair, String bar_interval, Integer count, Long millis_from, Long millis_to);
    abstract public ExchangeInfo getExchangeInfo();
    abstract public SymbolInfo getSymbolInfo(String symbol);
    abstract public List<TickerPrice> getAllPrices();
    abstract public List<AssetBalance> getAllBalances();
    abstract public AssetBalance getAssetBalance(String pair);
    
    abstract public Long order(boolean is_buy, boolean is_market, String pair, BigDecimal amount, BigDecimal price);
    abstract public Order getOrderStatus(String pair, long order_id) throws JsonParseException;
    abstract public void cancelOrder(String pair, long order_id);
    
    abstract public Trade getLastTrade(String pair);
    abstract public List<Trade> getAllTrades(String pair);
    abstract public List<Trade> getOrderHistory(String symbol, int limit);
    abstract public List<Order> getOpenOrders(String pair);
    
    abstract public BigDecimal getLastTradePrice(String symbolPair, boolean isBuyer);
    abstract public BigDecimal getCurrentPrice(String symbolPair);
    
    abstract public Closeable OnBarUpdateEvent(String pair, String barInterval, BarEvent evt, SocketClosedEvent onClosed);
    abstract public Closeable OnOrderEvent(String symbol, OrderEvent evt, SocketClosedEvent onClosed);
    abstract public Closeable OnBalanceEvent(BalanceEvent evt, SocketClosedEvent onClosed);
}
