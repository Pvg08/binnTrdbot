/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.api;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.SyncedTime;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import static com.binance.api.client.domain.account.NewOrder.*;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.RateLimit;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.domain.market.TickerStatistics;
import java.io.Closeable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.Decimal;

/**
 *
 * @author EVG_Adminer
 */
public class TradingAPIBinance extends TradingAPIAbstractInterface {

    private BinanceApiClientFactory factory = null;
    private BinanceApiRestClient client = null;
    
    private static final DecimalFormat df8 = new DecimalFormat("0.########");
    
    public TradingAPIBinance(String secret, String key) {
        super(secret, key);
    }

    @Override
    public boolean connect() {
        if (factory == null || client == null) {
            factory = BinanceApiClientFactory.newInstance(
                key,
                secret
            );
            client = factory.newRestClient();
        }
        return client != null;
    }

    @Override
    public boolean disconnect() {
        client = null;
        factory = null;
        return true;
    }

    @Override
    public long getServerTime() {
        return client.getServerTime();
    }

    @Override
    public long getAlignedCurrentTimeMillis() {
        return SyncedTime.getInstance(-1).currentTimeMillis();
    }

    @Override
    public List<RateLimit> getRateLimits() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void addBarsToList(List<Candlestick> nbars, List<Bar> result, String bar_interval) {
        Duration barDuration = Duration.ofSeconds(barIntervalToSeconds(bar_interval));
        for(int i=0; i<nbars.size(); i++) {
            Candlestick stick = nbars.get(i);
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(stick.getCloseTime()), ZoneId.systemDefault());
            Bar newbar = new BaseBar(barDuration, endTime, Decimal.valueOf(stick.getOpen()), Decimal.valueOf(stick.getHigh()), Decimal.valueOf(stick.getLow()), Decimal.valueOf(stick.getClose()), Decimal.valueOf(stick.getVolume()), Decimal.valueOf(stick.getQuoteAssetVolume()));
            result.add(newbar);
        }
    }
    
    private CandlestickInterval getCandlestickIntervalByString(String interval) {
        CandlestickInterval[] arr = CandlestickInterval.values();
        for (CandlestickInterval arr1 : arr) {
            if (arr1.getIntervalId().equals(interval)) {
                return arr1;
            }
        } 
        return null;
    }
    
    @Override
    public List<Bar> getBars(String pair, String bar_interval) {
        CandlestickInterval interval = getCandlestickIntervalByString(bar_interval);
        List<Candlestick> nbars = client.getCandlestickBars(pair, interval);
        List<Bar> result = new ArrayList<>(0);
        addBarsToList(nbars, result, bar_interval);
        return result;
    }

    @Override
    public List<Bar> getBars(String pair, String bar_interval, int bar_count, long millis_from, long millis_to) {
        CandlestickInterval interval = getCandlestickIntervalByString(bar_interval);
        List<Candlestick> nbars = client.getCandlestickBars(pair, interval, bar_count, millis_from, millis_to);
        List<Bar> result = new ArrayList<>(0);
        addBarsToList(nbars, result, bar_interval);
        return result;
    }
    
    @Override
    public ExchangeInfo getExchangeInfo() {
        return client.getExchangeInfo();
    }
    
    @Override
    public SymbolInfo getSymbolInfo(String symbol) {
        ExchangeInfo info = client.getExchangeInfo();
        SymbolInfo pair_sinfo = info.getSymbolInfo(symbol);
        return pair_sinfo;
    }

    @Override
    public List<TickerPrice> getAllPrices() {
        List<TickerPrice> allPrices = client.getAllPrices();
        return allPrices;
    }

    @Override
    public List<AssetBalance> getAllBalances() {
        Account account = client.getAccount();
        List<AssetBalance> allBalances = account.getBalances();
        return allBalances;
    }
    
    @Override
    public AssetBalance getAssetBalance(String pair) {
        Account account = client.getAccount();
        return account.getAssetBalance(pair);
    }
    
    @Override
    public long order(boolean is_buy, boolean is_market, String pair, BigDecimal amount, BigDecimal price) {
        NewOrderResponse newOrderResponse = null;
        if (is_buy) {
            if (is_market) {
                newOrderResponse = client.newOrder(marketBuy(pair, df8.format(amount).replace(".","").replace(",",".")));
            } else {
                newOrderResponse = client.newOrder(limitBuy(pair, TimeInForce.GTC, df8.format(amount).replace(".","").replace(",","."), df8.format(price).replace(".","").replace(",",".")));
            }
        } else {
            if (is_market) {
                newOrderResponse = client.newOrder(marketSell(pair, df8.format(amount).replace(".","").replace(",",".")));
            } else {
                newOrderResponse = client.newOrder(limitSell(pair, TimeInForce.GTC, df8.format(amount).replace(".","").replace(",","."), df8.format(price).replace(".","").replace(",",".")));
            }
        }
        return (newOrderResponse != null && newOrderResponse.getOrderId()>0) ? newOrderResponse.getOrderId() : -1;
    }

    @Override
    public Order getOrderStatus(String pair, long order_id) {
        return client.getOrderStatus(new OrderStatusRequest(pair, order_id));
    }

    @Override
    public void cancelOrder(String pair, long order_id) {
        client.cancelOrder(new CancelOrderRequest(pair, order_id));
    }

    @Override
    public Trade getLastTrade(String pair) {
        int i, imax = -1;
        long max_buy_time = 0;
        List<Trade> myTrades = client.getMyTrades(pair);
        for (i=0; i < myTrades.size(); i++) {
            if (myTrades.get(i).isBuyer() && max_buy_time < myTrades.get(i).getTime()) {
                max_buy_time = myTrades.get(i).getTime();
                imax = i;
            }
        }
        if (imax >= 0) {
            return myTrades.get(imax);
        }
        return null;
    }

    @Override
    public List<Order> getOpenOrders(String pair) {
        return client.getOpenOrders(new OrderRequest(pair));
    }

    @Override
    public BigDecimal getLastTradePrice(String symbolPair, boolean isBuyer) {
        int i, imax = -1;
        long max_buy_time = 0;
        List<Trade> myTrades = client.getMyTrades(symbolPair);
        for (i=0; i < myTrades.size(); i++) {
            if (myTrades.get(i).isBuyer() == isBuyer && max_buy_time < myTrades.get(i).getTime()) {
                max_buy_time = myTrades.get(i).getTime();
                imax = i;
            }
        }
        if (imax >= 0) {
            return new BigDecimal(myTrades.get(imax).getPrice());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getCurrentPrice(String symbolPair) {
        TickerStatistics tc = client.get24HrPriceStatistics(symbolPair);
        return tc != null ? (new BigDecimal(tc.getLastPrice())) : null;
    }

    @Override
    public Closeable OnBarUpdateEvent(String symbol, String barInterval, BarEvent evt) {
        BinanceApiWebSocketClient client_s = BinanceApiClientFactory.newInstance().newWebSocketClient();
        Closeable socket = client_s.onCandlestickEvent(symbol.toLowerCase(), getCandlestickIntervalByString(barInterval), response -> {
            Duration barDuration = Duration.ofSeconds(barIntervalToSeconds(barInterval));
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(response.getCloseTime()), ZoneId.systemDefault());
            Bar nbar = new BaseBar(
                    barDuration, 
                    endTime, 
                    Decimal.valueOf(response.getOpen()), 
                    Decimal.valueOf(response.getHigh()), 
                    Decimal.valueOf(response.getLow()), 
                    Decimal.valueOf(response.getClose()), 
                    Decimal.valueOf(response.getVolume()), 
                    Decimal.valueOf(response.getQuoteAssetVolume())
            );
            evt.onUpdate(nbar, response.getBarFinal());
        });
        
        /*
        if (client_s != null) {
            client_s.close();
            client_s = null;
        }
        */
        
        return socket;
    }
    
    @Override
    public Closeable OnOrderEvent(String symbol, OrderEvent evt) {
        BinanceApiWebSocketClient client_s = factory.newWebSocketClient();
        String listenKey = client.startUserDataStream();
        Closeable socket = client_s.onUserDataUpdateEvent(listenKey, response -> {
            if (
                    response.getEventType() == UserDataUpdateEventType.ORDER_TRADE_UPDATE &&
                    response.getOrderTradeUpdateEvent().getEventType().equals("executionReport") && 
                    response.getOrderTradeUpdateEvent().getOrderId() > 0 &&
                    Double.parseDouble(response.getOrderTradeUpdateEvent().getAccumulatedQuantity()) > 0 &&
                    (
                        symbol == null ||
                        response.getOrderTradeUpdateEvent().getSymbol().toUpperCase().equals(symbol)
                    )
            ) {
                evt.onUpdate(response.getOrderTradeUpdateEvent());
            }
        });
        return socket;
    }
}
