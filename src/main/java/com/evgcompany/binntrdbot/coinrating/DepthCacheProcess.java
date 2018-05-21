/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.coinrating;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import java.io.Closeable;
import java.io.IOException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author EVG_Adminer
 */
public class DepthCacheProcess {

    private final String symbol;

    private static final String BIDS = "BIDS";
    private static final String ASKS = "ASKS";

    private long lastUpdateId;
    private long lastUpdateMillis = 0;

    private Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache;

    private Closeable socket = null;
    
    private int registered = 0;

    public DepthCacheProcess(String symbol) {
        this.symbol = symbol;
    }

    public void startProcess() {
        initializeDepthCache();
        startDepthEventStreaming();
        System.out.println("Depth check for " + symbol + " is started.");
    }
    public void stopProcess() {
        if (registered > 0) return;
        depthCache.clear();
        lastUpdateId = 0;
        stopDepthEventStreaming();
        System.out.println("Depth check for " + symbol + " is stopped.");
    }
    
    public void registerNew() {
        registered++;
    }
    public void unregister() {
        registered--;
    }
    
    /**
     * Initializes the depth cache by using the REST API.
     */
    private void initializeDepthCache() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
        BinanceApiRestClient client = factory.newRestClient();
        OrderBook orderBook = client.getOrderBook(symbol.toUpperCase(), 10);

        this.depthCache = new HashMap<>();
        this.lastUpdateId = orderBook.getLastUpdateId();

        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());
        orderBook.getAsks().forEach((ask) -> {
            asks.put(new BigDecimal(ask.getPrice()), new BigDecimal(ask.getQty()));
        });
        depthCache.put(ASKS, asks);

        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        orderBook.getBids().forEach((bid) -> {
            bids.put(new BigDecimal(bid.getPrice()), new BigDecimal(bid.getQty()));
        });
        depthCache.put(BIDS, bids);
        
        lastUpdateMillis = System.currentTimeMillis();
    }

    /**
     * Begins streaming of depth events.
     */
    private void startDepthEventStreaming() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
        BinanceApiWebSocketClient client = factory.newWebSocketClient();
        socket = client.onDepthEvent(symbol.toLowerCase(), response -> {
            if (response.getUpdateId() > lastUpdateId) {
                //System.out.println(response);
                lastUpdateId = response.getUpdateId();
                lastUpdateMillis = System.currentTimeMillis();
                updateOrderBook(getAsks(), response.getAsks());
                updateOrderBook(getBids(), response.getBids());
                //printDepthCache();
            }
        });
    }

    private void stopDepthEventStreaming() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(DepthCacheProcess.class.getName()).log(Level.SEVERE, null, ex);
            }
            socket = null;
        }
    }

    public long getMillisFromLastUpdate() {
        return System.currentTimeMillis() - lastUpdateMillis;
    }
    
    public boolean isStopped() {
        return socket == null;
    }

    /**
     * Updates an order book (bids or asks) with a delta received from the
     * server.
     *
     * Whenever the qty specified is ZERO, it means the price should was removed
     * from the order book.
     */
    private void updateOrderBook(NavigableMap<BigDecimal, BigDecimal> lastOrderBookEntries, List<OrderBookEntry> orderBookDeltas) {
        orderBookDeltas.forEach((orderBookDelta) -> {
            BigDecimal price = new BigDecimal(orderBookDelta.getPrice());
            BigDecimal qty = new BigDecimal(orderBookDelta.getQty());
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                // qty=0 means remove this level
                lastOrderBookEntries.remove(price);
            } else {
                lastOrderBookEntries.put(price, qty);
            }
        });
    }

    public NavigableMap<BigDecimal, BigDecimal> getAsks() {
        return depthCache.get(ASKS);
    }

    public NavigableMap<BigDecimal, BigDecimal> getBids() {
        return depthCache.get(BIDS);
    }

    /**
     * @return the best ask in the order book
     */
    public Map.Entry<BigDecimal, BigDecimal> getBestAsk() {
        return !isStopped() ? getAsks().lastEntry() : null;
    }

    /**
     * @return the best bid in the order book
     */
    public Map.Entry<BigDecimal, BigDecimal> getBestBid() {
        return !isStopped() ? getBids().firstEntry() : null;
    }

    /**
     * @return a depth cache, containing two keys (ASKs and BIDs), and for each,
     * an ordered list of book entries.
     */
    public Map<String, NavigableMap<BigDecimal, BigDecimal>> getDepthCache() {
        return depthCache;
    }

    /**
     * Prints the cached order book / depth of a symbol as well as the best ask
     * and bid price in the book.
     */
    public void printDepthCache() {
        System.out.println(depthCache);
        System.out.println("ASKS: " + getAsks().size());
        getAsks().entrySet().forEach(entry -> System.out.println(toDepthCacheEntryString(entry)));
        System.out.println("BIDS: " + getBids().size());
        getBids().entrySet().forEach(entry -> System.out.println(toDepthCacheEntryString(entry)));
        System.out.println("BEST ASK: " + toDepthCacheEntryString(getBestAsk()));
        System.out.println("BEST BID: " + toDepthCacheEntryString(getBestBid()));
    }

    /**
     * Pretty prints an order book entry in the format "price / quantity".
     */
    private static String toDepthCacheEntryString(Map.Entry<BigDecimal, BigDecimal> depthCacheEntry) {
        return depthCacheEntry.getKey().toPlainString() + " / " + depthCacheEntry.getValue();
    }
}
