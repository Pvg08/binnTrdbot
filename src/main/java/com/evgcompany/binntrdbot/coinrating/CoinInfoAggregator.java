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
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author EVG_adm_T
 */
public class CoinInfoAggregator {
    
    private Set<String> quoteAssets = null;
    private Set<String> baseAssets = null;
    private Set<String> coins = null;
    private Set<String> coinsToCheck = null;
    private TradingAPIAbstractInterface client = null;
    private final Map<String, String[]> coinPairs = new HashMap<>();
    private final Map<String, Double> initialPrices = new HashMap<>();
    
    private final String baseCoin = "BTC";
    private final double baseCoinMinCount = 0.001;
    
    public CoinInfoAggregator(TradingAPIAbstractInterface client) {
        this.client = client;
    }
    
    private double convertSumm(String symbol1, double price, String symbol2) {
        if (initialPrices.containsKey(symbol1+symbol2)) {
            return price * initialPrices.get(symbol1+symbol2);
        } else if (initialPrices.containsKey(symbol2+symbol1)) {
            return price / initialPrices.get(symbol2+symbol1);
        }
        return 0;
    }
    
    public void init() {
        baseAssets = new HashSet<>();
        quoteAssets = new HashSet<>();
        coins = new HashSet<>();

        List<TickerPrice> allPrices = client.getAllPrices();
        allPrices.forEach((price) -> {
            if (price != null && !price.getSymbol().isEmpty()) {
                SymbolInfo info = client.getSymbolInfo(price.getSymbol().toUpperCase());
                if (info != null && info.getStatus() == SymbolStatus.TRADING) {
                    coinPairs.put(info.getBaseAsset() + info.getQuoteAsset(), new String[]{info.getBaseAsset(), info.getQuoteAsset()});
                    initialPrices.put(info.getBaseAsset() + info.getQuoteAsset(), Double.parseDouble(price.getPrice()));
                    quoteAssets.add(info.getQuoteAsset());
                    baseAssets.add(info.getBaseAsset());
                    coins.add(info.getBaseAsset());
                    coins.add(info.getQuoteAsset());
                }
            }
        });
        
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
    public Map<String, Double> getInitialPrices() {
        return initialPrices;
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
}
