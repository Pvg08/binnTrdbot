/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author EVG_Adminer
 */
public class TradePairProcessController {
    
    private OrdersController ordersController = null;
    private final List<TradePairProcess> pairs = new ArrayList<>(0);
    
    private String mainStrategy;
    private String barsInterval;
    private int barAdditionalCount;
    private int tradingBalancePercent;
    private long updateDelay;
    private boolean checkOtherStrategies = false;
    private boolean buyStop = false;
    private boolean sellStop = false;
    private boolean allPairsWavesUsage = false;
    private int buyStopLimitedTimeout;
    private int sellStopLimitedTimeout;
    
    public TradePairProcessController(OrdersController ordersController) {
        this.ordersController = ordersController;
    }
    
    private int searchCurrencyFirstPair(String currencyPair) {
        for(int i=0; i<pairs.size(); i++) {
            if (pairs.get(i).getSymbol().equals(currencyPair)) {
                return i;
            }
        }
        return -1;
    }
    
    public List<TradePairProcess> getPairs() {
        return pairs;
    }
    
    public boolean hasPair(String currencyPair) {
        return searchCurrencyFirstPair(currencyPair) >= 0;
    }
    
    private TradePairProcess initializePair(String symbol, boolean run) {
        boolean has_wave = symbol.contains("~");
        boolean has_plus = symbol.contains("+");
        boolean has_2plus = symbol.contains("++");
        boolean has_minus = !has_plus && symbol.contains("-");
        symbol = symbol.replaceAll("\\-", "").replaceAll("\\+", "").replaceAll("\\~", "");
        int pair_index = searchCurrencyFirstPair(symbol);
        TradePairProcess nproc;
        if (pair_index < 0) {
            if (has_wave || allPairsWavesUsage) {
                nproc = new TradePairWaveProcess(ordersController.getClient(), ordersController, symbol);
            } else {
                nproc = new TradePairProcess(ordersController.getClient(), ordersController, symbol);
            }
            nproc.setStartDelay(pairs.size() * 1000 + 500);
            nproc.setTryingToSellUp(has_plus);
            nproc.setSellUpAll(has_2plus);
        } else {
            nproc = pairs.get(pair_index);
        }

        nproc.setTryingToBuyDip(has_minus);
        nproc.set_do_remove_flag(false);
        nproc.setTradingBalancePercent(tradingBalancePercent);
        nproc.setMainStrategy(mainStrategy);
        nproc.setBarInterval(barsInterval);
        nproc.setDelayTime(updateDelay);
        nproc.setCheckOtherStrategies(checkOtherStrategies);
        nproc.setUseBuyStopLimited(buyStop);
        nproc.setUseSellStopLimited(sellStop);
        nproc.setStopBuyLimitTimeout(buyStopLimitedTimeout);
        nproc.setStopSellLimitTimeout(sellStopLimitedTimeout);
        nproc.setBarQueryCount(barAdditionalCount);
        
        if (run && pair_index < 0) {
            nproc.start();
            pairs.add(nproc);
        }
        
        return nproc;
    }
    
    public void initBasePairs(String pairstxt) {
        pairs.forEach((pair)->{
            pair.set_do_remove_flag(true);
        });
        List<String> items = Arrays.asList(pairstxt.toUpperCase().split("\\s*,\\s*")).stream().distinct().collect(Collectors.toList());
        if (items.size() > 0) {
            items.forEach((symbol)->{
                if (!symbol.isEmpty()) {
                    initializePair(symbol, true);
                }
            });
        }
        int i = 0;
        while (i<pairs.size()) {
            if (pairs.get(i).is_do_remove_flag()) {
                removePair(i);
            } else {
                i++;
            }
        }
    }
    
    public TradePairProcess addPair(TradePairProcess newProc) {
        pairs.add(newProc);
        newProc.start();
        return newProc;
    }
    public TradePairProcess addPair(String newPair) {
        return initializePair(newPair, true);
    }
    public TradePairProcess addPairFastRun(String newPair) {
        if (!hasPair(newPair)) {
            TradePairProcess nproc = initializePair(newPair, false);
            nproc.setTryingToSellUp(false);
            nproc.setSellUpAll(false);
            nproc.setTryingToBuyDip(false);
            nproc.set_do_remove_flag(false);
            nproc.setTradingBalancePercent(tradingBalancePercent);
            nproc.setMainStrategy("Auto");
            nproc.setBarInterval("5m");
            nproc.setDelayTime(8);
            nproc.setBuyOnStart(true);
            nproc.setStopBuyLimitTimeout(120);
            nproc.setStopSellLimitTimeout(300);
            nproc.setUseBuyStopLimited(true);
            nproc.setUseSellStopLimited(true);
            return addPair(nproc);
        }
        return null;
    }
    
    public void stopPairs() {
        if (pairs.size() > 0) {
            pairs.forEach((pair)->{
                pair.doStop();
            });
            pairs.clear();
        }
    }
    
    public void pausePairs(boolean is_paused) {
        if (pairs.size() > 0) {
            pairs.forEach((pair)->{
                pair.doSetPaused(is_paused);
            });
        }
    }
    
    public void removePair(String currencyPair) {
        if (pairs.size() > 0) {
            int pair_index = searchCurrencyFirstPair(currencyPair);
            if (pair_index >= 0 && pair_index < pairs.size()) {
                removePair(pair_index);
            }
        }
    }
    
    private void removePair(int pairIndex) {
        pairs.get(pairIndex).doStop();
        ordersController.removeCurrencyPair(pairs.get(pairIndex).getSymbol());
        pairs.remove(pairIndex);
    }
    
    public void pairAction(int index, String action) {
        if (pairs.size() > 0 && index >= 0) {
            String currencyPair = ordersController.getNthCurrencyPair(index);
            int pair_index = searchCurrencyFirstPair(currencyPair);
            if (pair_index >= 0 && pair_index < pairs.size()) {
                switch (action) {
                    case "REMOVE":
                        removePair(pair_index);
                        break;
                    case "BUY":
                        pairs.get(pair_index).doBuy();
                        break;
                    case "SELL":
                        pairs.get(pair_index).doSell();
                        break;
                    case "CANCEL":
                        pairs.get(pair_index).doLimitCancel();
                        break;
                    case "STATISTICS":
                        pairs.get(pair_index).doShowStatistics();
                        break;
                    case "PLOT":
                        pairs.get(pair_index).doShowPlot();
                        break;
                    case "BROWSER":
                        if (Desktop.isDesktopSupported()) {
                            try {
                                String url = "https://www.binance.com/tradeDetail.html?symbol="+pairs.get(pair_index).getBaseSymbol()+"_"+pairs.get(pair_index).getQuoteSymbol();
                                Desktop.getDesktop().browse(new URI(url));
                            } catch (URISyntaxException | IOException ex) {
                                Logger.getLogger(mainApplication.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        break;
                    case "NNBTRAIN":
                        pairs.get(pair_index).doNetworkAction("TRAIN", "BASE");
                        break;
                    case "NNCTRAIN":
                        pairs.get(pair_index).doNetworkAction("TRAIN", "COIN");
                        break;
                    case "NNBADD":
                        pairs.get(pair_index).doNetworkAction("ADDSET", "BASE");
                        break;
                    case "NNCADD":
                        pairs.get(pair_index).doNetworkAction("ADDSET", "COIN");
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * @return the mainStrategy
     */
    public String getMainStrategy() {
        return mainStrategy;
    }

    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        this.mainStrategy = mainStrategy;
    }

    /**
     * @return the barsInterval
     */
    public String getBarsInterval() {
        return barsInterval;
    }

    /**
     * @param barsInterval the barsInterval to set
     */
    public void setBarsInterval(String barsInterval) {
        this.barsInterval = barsInterval;
    }

    /**
     * @return the barAdditionalCount
     */
    public int getBarAdditionalCount() {
        return barAdditionalCount;
    }

    /**
     * @param barAdditionalCount the barAdditionalCount to set
     */
    public void setBarAdditionalCount(int barAdditionalCount) {
        this.barAdditionalCount = barAdditionalCount;
    }

    /**
     * @return the tradingBalancePercent
     */
    public int getTradingBalancePercent() {
        return tradingBalancePercent;
    }

    /**
     * @param tradingBalancePercent the tradingBalancePercent to set
     */
    public void setTradingBalancePercent(int tradingBalancePercent) {
        this.tradingBalancePercent = tradingBalancePercent;
    }

    /**
     * @return the updateDelay
     */
    public long getUpdateDelay() {
        return updateDelay;
    }

    /**
     * @param updateDelay the updateDelay to set
     */
    public void setUpdateDelay(long updateDelay) {
        this.updateDelay = updateDelay;
    }

    /**
     * @return the checkOtherStrategies
     */
    public boolean isCheckOtherStrategies() {
        return checkOtherStrategies;
    }

    /**
     * @param checkOtherStrategies the checkOtherStrategies to set
     */
    public void setCheckOtherStrategies(boolean checkOtherStrategies) {
        this.checkOtherStrategies = checkOtherStrategies;
    }

    /**
     * @return the buyStop
     */
    public boolean isBuyStop() {
        return buyStop;
    }

    /**
     * @param buyStop the buyStop to set
     */
    public void setBuyStop(boolean buyStop) {
        this.buyStop = buyStop;
    }

    /**
     * @return the sellStop
     */
    public boolean isSellStop() {
        return sellStop;
    }

    /**
     * @param sellStop the sellStop to set
     */
    public void setSellStop(boolean sellStop) {
        this.sellStop = sellStop;
    }

    /**
     * @return the buyStopLimitedTimeout
     */
    public int getBuyStopLimitedTimeout() {
        return buyStopLimitedTimeout;
    }

    /**
     * @param buyStopLimitedTimeout the buyStopLimitedTimeout to set
     */
    public void setBuyStopLimitedTimeout(int buyStopLimitedTimeout) {
        this.buyStopLimitedTimeout = buyStopLimitedTimeout;
    }

    /**
     * @return the sellStopLimitedTimeout
     */
    public int getSellStopLimitedTimeout() {
        return sellStopLimitedTimeout;
    }

    /**
     * @param sellStopLimitedTimeout the sellStopLimitedTimeout to set
     */
    public void setSellStopLimitedTimeout(int sellStopLimitedTimeout) {
        this.sellStopLimitedTimeout = sellStopLimitedTimeout;
    }
    
    public OrdersController getOrdersController() {
        return ordersController;
    }

    /**
     * @return the wavesUsage
     */
    public boolean isAllPairsWavesUsage() {
        return allPairsWavesUsage;
    }

    /**
     * @param wavesUsage the wavesUse to set
     */
    public void setAllPairsWavesUsage(boolean wavesUsage) {
        this.allPairsWavesUsage = wavesUsage;
    }
}
