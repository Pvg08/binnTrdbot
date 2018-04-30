/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.coinrating;

import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.TickerPrice;
import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author EVG_adm_T
 */
public class CoinInfoAggregator extends PeriodicProcessThread {
    
    private final Semaphore SEMAPHORE_UPDATE = new Semaphore(1, true);
    
    private Set<String> quoteAssets = null;
    private Set<String> baseAssets = null;
    private Set<String> coins = null;
    private Set<String> coinsToCheck = null;
    private final Map<String, String[]> coinPairs = new HashMap<>();
    private final Map<String, Double> lastPrices = new HashMap<>();
    
    private TradingAPIAbstractInterface client = null;
    
    private final String baseCoin = "BTC";
    private final double baseCoinMinCount = 0.001;
    private double baseAccountCost = 0;
    
    private boolean init_complete = false;
    private long last_init_millis = 0;
    private long reinit_millis = 24 * 60 * 60 * 1000;
    
    public CoinInfoAggregator(TradingAPIAbstractInterface client) {
        this.client = client;
        delayTime = 60;
    }
    
    public double convertSumm(String symbol1, double price, String symbol2) {
        if (symbol1.equals(symbol2)) {
            return price;
        } else if (lastPrices.containsKey(symbol1+symbol2)) {
            return price * lastPrices.get(symbol1+symbol2);
        } else if (lastPrices.containsKey(symbol2+symbol1)) {
            return price / lastPrices.get(symbol2+symbol1);
        }
        return 0;
    }
    
    private double getAccountCost(String coin) {
        double accountCost = 0;
        List<AssetBalance> allBalances = client.getAllBalances();
        for(AssetBalance balance : allBalances) {
            if (coins.contains(balance.getAsset())) {
                accountCost += convertSumm(balance.getAsset(), Double.parseDouble(balance.getFree()) + Double.parseDouble(balance.getLocked()), coin);
            }
        }
        return accountCost;
    }
    
    private void updateBaseAccountCost() {
        baseAccountCost = getAccountCost(baseCoin);
    }
    
    private void updatePrices() {
        List<TickerPrice> allPrices = client.getAllPrices();
        allPrices.forEach((price) -> {
            lastPrices.put(price.getSymbol(), Double.parseDouble(price.getPrice()));
        });
    }
    
    private void doInitCoinsToCheck() {
        coinsToCheck = new HashSet<>();
        List<AssetBalance> allBalances = client.getAllBalances();
        allBalances.forEach((balance) -> {
            if (
                    (Float.parseFloat(balance.getFree()) + Float.parseFloat(balance.getLocked())) >= 0.00000001 && 
                    !balance.getAsset().equals("BNB")
            ) {
                coinsToCheck.add(balance.getAsset());
            }
        });

        Set<String> oldCoinsToCheck = coinsToCheck;
        coinsToCheck = new HashSet<>();
        oldCoinsToCheck.forEach((checkCoin) -> {
            AssetBalance balance = client.getAssetBalance(checkCoin);
            if (balance != null && !balance.getAsset().isEmpty()) {
                double full_balance = Double.parseDouble(balance.getFree()) + Double.parseDouble(balance.getLocked());
                if (
                    (checkCoin.equals(baseCoin) && full_balance >= baseCoinMinCount) || 
                    convertSumm(checkCoin, full_balance, baseCoin) >= baseCoinMinCount
                ) {
                    coinsToCheck.add(checkCoin);
                }
            }
        });
    }
    
    private void doMainInit() {
        
        try {
            SEMAPHORE_UPDATE.acquire();
            
            last_init_millis = System.currentTimeMillis();
        
            baseAssets = new HashSet<>();
            quoteAssets = new HashSet<>();
            coins = new HashSet<>();

            List<TickerPrice> allPrices = client.getAllPrices();
            if (allPrices!= null && !allPrices.isEmpty()) {
                allPrices.forEach((price) -> {
                    if (price != null && !price.getSymbol().isEmpty()) {
                        lastPrices.put(price.getSymbol(), Double.parseDouble(price.getPrice()));
                        SymbolInfo info = client.getSymbolInfo(price.getSymbol().toUpperCase());
                        if (info != null && info.getStatus() == SymbolStatus.TRADING) {
                            coinPairs.put(info.getBaseAsset() + info.getQuoteAsset(), new String[]{info.getBaseAsset(), info.getQuoteAsset()});
                            lastPrices.put(info.getBaseAsset() + info.getQuoteAsset(), Double.parseDouble(price.getPrice()));
                            quoteAssets.add(info.getQuoteAsset());
                            baseAssets.add(info.getBaseAsset());
                            coins.add(info.getBaseAsset());
                            coins.add(info.getQuoteAsset());
                        }
                    }
                });
            }
            
            SEMAPHORE_UPDATE.release();
            
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinInfoAggregator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * @return the quotePrices
     */
    public Set<String> getQuoteAssets() {
        return quoteAssets;
    }

    /**
     * @return the baseAssets
     */
    public Set<String> getBaseAssets() {
        return baseAssets;
    }

    /**
     * @return the coins
     */
    public Set<String> getCoins() {
        return coins;
    }

    /**
     * @return the coinsToCheck
     */
    public Set<String> getCoinsToCheck() {
        return coinsToCheck;
    }

    /**
     * @return the client
     */
    public TradingAPIAbstractInterface getClient() {
        return client;
    }

    /**
     * @return the coinPairs
     */
    public Map<String, String[]> getCoinPairs() {
        return coinPairs;
    }

    /**
     * @return the initialPrices
     */
    public Map<String, Double> getLastPrices() {
        return lastPrices;
    }

    /**
     * @return the baseCoin
     */
    public String getBaseCoin() {
        return baseCoin;
    }

    /**
     * @return the baseCoinMinCount
     */
    public double getBaseCoinMinCount() {
        return baseCoinMinCount;
    }

    @Override
    protected void runStart() {
        init_complete = false;
        doMainInit();
        doInitCoinsToCheck();
        updateBaseAccountCost();
        updatePrices();
        init_complete = true;
    }

    @Override
    protected void runBody() {
        if ((System.currentTimeMillis() - last_init_millis) > reinit_millis) {
            init_complete = false;
            doMainInit();
            init_complete = true;
        } else {
            updatePrices();
        }
        updateBaseAccountCost();
    }

    @Override
    protected void runFinish() {
        // nothing
    }

    public boolean isInitialised() {
        return init_complete;
    }

    /**
     * @return the SEMAPHORE_UPDATE
     */
    public Semaphore getSemaphore() {
        return SEMAPHORE_UPDATE;
    }

    /**
     * @return the baseAccountCost
     */
    public double getBaseAccountCost() {
        return baseAccountCost;
    }
}
