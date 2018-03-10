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
import com.evgcompany.binntrdbot.tradePairProcess;
import java.io.Closeable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_adm_T
 */
public abstract class TradingAPIAbstractInterface {
    String secret, key;
    
    public TradingAPIAbstractInterface(String secret, String key) {
        this.secret = secret;
        this.key = key;
    }
    
    abstract public boolean connect();
    abstract public boolean disconnect();
    
    public long getServerTime() {
        return System.currentTimeMillis();
    }
    
    public long getAlignedCurrentTimeMillis() {
        return System.currentTimeMillis();
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
    
    public void addSeriesBars(TimeSeries series, List<Bar> bars) {
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
    
    abstract public List<RateLimit> getRateLimits();
    abstract public List<Bar> getBars(String pair, String bar_interval);
    abstract public List<Bar> getBars(String pair, String bar_interval, int count, long millis_from, long millis_to);
    abstract public ExchangeInfo getExchangeInfo();
    abstract public SymbolInfo getSymbolInfo(String symbol);
    abstract public List<TickerPrice> getAllPrices();
    abstract public List<AssetBalance> getAllBalances();
    abstract public AssetBalance getAssetBalance(String pair);
    
    abstract public long order(boolean is_buy, boolean is_market, String pair, BigDecimal amount, BigDecimal price);
    abstract public Order getOrderStatus(String pair, long order_id);
    abstract public void cancelOrder(String pair, long order_id);
    
    abstract public Trade getLastTrade(String pair);
    abstract public List<Order> getOpenOrders(String pair);
    
    abstract public BigDecimal getLastTradePrice(String symbolPair, boolean isBuyer);
    abstract public BigDecimal getCurrentPrice(String symbolPair);
    
    abstract public Closeable OnBarUpdateEvent(String pair, String barInterval, BarEvent evt);
}
