/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import java.awt.Desktop;
import java.io.IOException;
import java.math.BigDecimal;
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
public class TradePairProcessList {
    
    private OrdersController ordersController = null;
    private final List<AbstractTradePairProcess> pairs = new ArrayList<>(0);
    
    // main pair process
    private String mainStrategy;
    private String barsInterval;
    private int barAdditionalCount;
    private int tradingBalancePercent;
    private double tradingBalanceMainValue;
    private long updateDelay;
    private boolean checkOtherStrategies = false;
    private boolean buyStop = false;
    private boolean sellStop = false;
    private int buyStopLimitedTimeout;
    private int sellStopLimitedTimeout;
    private int pyramidAutoMaxSize;
    
    // wave pair process
    private boolean allPairsWavesUsage = false;
    private double wavesTradingBalancePercent;
    private double wavesTradingBalanceMainValue;
    private double wavesSecondaryPercent;
    private double wavesIncKoef;
    private double wavesInitialProfitPercent;
    private double wavesMinProfitPercent;
    private double wavesHalfDivideProfitOrdersCount;
    
    public TradePairProcessList() {
        this.ordersController = OrdersController.getInstance();
    }
    
    public List<AbstractTradePairProcess> getPairs() {
        return pairs;
    }

    private int searchCurrencyFirstPair(String currencyPair) {
        for(int i=0; i<pairs.size(); i++) {
            if (pairs.get(i).getSymbol().equals(currencyPair)) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasPair(String currencyPair) {
        return searchCurrencyFirstPair(currencyPair) >= 0;
    }

    private AbstractTradePairProcess initializePair(String symbol, boolean run) {
        boolean has_short = symbol.contains("_");
        boolean has_wave = symbol.contains("~");
        boolean has_quick = symbol.contains("`");
        boolean has_plus = symbol.contains("+");
        boolean has_2plus = symbol.contains("++");
        boolean has_minus = !has_plus && symbol.contains("-");
        symbol = symbol.replaceAll("\\-", "").
                replaceAll("\\+", "").
                replaceAll("\\~", "").
                replaceAll("\\_", "").
                replaceAll("\\`", "");
        int pair_index = searchCurrencyFirstPair(symbol);
        AbstractTradePairProcess nproc;
        if (pair_index < 0) {
            if (has_wave || (allPairsWavesUsage && !has_short && !has_quick && !has_plus && !has_2plus && !has_minus)) {
                nproc = new TradePairWaveProcess(ordersController.getClient(), symbol);
            } else if (has_quick) {
                nproc = new TradePairQuickWavesProcess(ordersController.getClient(), symbol);
            } else {
                nproc = new TradePairProcess(ordersController.getClient(), symbol);
                ((TradePairProcess)nproc).setTryingToSellOnPeak(has_plus);
                ((TradePairProcess)nproc).setSellOpenOrdersOnPeak(has_2plus);
            }
            nproc.setStartDelay(pairs.size() * 1000 + 500);
            
        } else {
            nproc = pairs.get(pair_index);
        }

        nproc.setNeedToRemove(false);
        nproc.setBarInterval(barsInterval);
        nproc.setDelayTime(updateDelay);
        nproc.setUseBuyStopLimited(buyStop);
        nproc.setUseSellStopLimited(sellStop);
        nproc.setStopBuyLimitTimeout(buyStopLimitedTimeout);
        nproc.setStopSellLimitTimeout(sellStopLimitedTimeout);
        nproc.setBarQueryCount(barAdditionalCount);
        nproc.setLongModeAuto(!has_short);
        nproc.setPyramidAutoMaxSize(pyramidAutoMaxSize);
        nproc.setTradingBalancePercent(tradingBalancePercent);
        nproc.setTradingBalanceMainValue(tradingBalanceMainValue);
        
        if (nproc instanceof TradePairStrategyProcess) {
            ((TradePairStrategyProcess)nproc).setMainStrategy(mainStrategy);
            ((TradePairStrategyProcess)nproc).setCheckOtherStrategies(checkOtherStrategies);
        }
        if (nproc instanceof TradePairProcess) {
            ((TradePairProcess)nproc).setTryingToBuyDip(has_minus);
        }
        if (nproc instanceof TradePairWaveProcess) {
            ((TradePairWaveProcess)nproc).setTradingBalancePercent(wavesTradingBalancePercent);
            ((TradePairWaveProcess)nproc).setTradingBalanceMainValue(wavesTradingBalanceMainValue);
            ((TradePairWaveProcess)nproc).setWavesIncKoef(BigDecimal.valueOf(wavesIncKoef));
            ((TradePairWaveProcess)nproc).setWavesSecondaryPercent(BigDecimal.valueOf(wavesSecondaryPercent));
            ((TradePairWaveProcess)nproc).setMinProfitPercent(wavesMinProfitPercent);
            ((TradePairWaveProcess)nproc).setInitialProfitPercent(wavesInitialProfitPercent);
            ((TradePairWaveProcess)nproc).setHalfDivideProfitOrdersCount(wavesHalfDivideProfitOrdersCount);
        }
        
        if (run && pair_index < 0) {
            nproc.start();
            pairs.add(nproc);
        }
        
        return nproc;
    }
    
    public void initBasePairs(String pairstxt) {
        pairs.forEach((pair)->{
            pair.setNeedToRemove(true);
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
            if (pairs.get(i).needToRemove()) {
                removePair(i);
            } else {
                i++;
            }
        }
    }
    
    public AbstractTradePairProcess addPair(AbstractTradePairProcess newProc) {
        pairs.add(newProc);
        newProc.start();
        return newProc;
    }
    public AbstractTradePairProcess addPair(String newPair) {
        return initializePair(newPair, true);
    }
    public AbstractTradePairProcess addPairFastRun(String newPair) {
        if (!hasPair(newPair)) {
            AbstractTradePairProcess nproc = initializePair(newPair, false);
            nproc.setNeedToRemove(false);
            nproc.setBarInterval("5m");
            nproc.setDelayTime(8);
            nproc.setStopBuyLimitTimeout(120);
            nproc.setStopSellLimitTimeout(300);
            nproc.setUseBuyStopLimited(true);
            nproc.setUseSellStopLimited(true);
            nproc.setPyramidAutoMaxSize(1);

            if (nproc instanceof TradePairStrategyProcess) {
                ((TradePairStrategyProcess)nproc).setMainStrategy("Auto");
            }
            if (nproc instanceof TradePairProcess) {
                ((TradePairProcess)nproc).setTryingToSellOnPeak(false);
                ((TradePairProcess)nproc).setSellOpenOrdersOnPeak(false);
                ((TradePairProcess)nproc).setTryingToBuyDip(false);
                ((TradePairProcess)nproc).setBuyOnStart(true);
            }
            if (nproc instanceof TradePairWaveProcess) {
                ((TradePairWaveProcess)nproc).setMinProfitPercent(0.1);
            }

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
        //ordersController.removeCurrencyPair(pairs.get(pairIndex).getOrderCID());
        pairs.remove(pairIndex);
    }
    
    public void pairAction(int index, String action) {
        if (pairs.size() > 0 && index >= 0) {
            ControllableOrderProcess controllableProcess = ordersController.getNthControllableOrderProcess(index);
            if (controllableProcess != null) {
                AbstractTradePairProcess pairProcess = null;
                TradePairStrategyNNProcess tradePairNNProcess = null;
                if (controllableProcess instanceof AbstractTradePairProcess) {
                    pairProcess = (AbstractTradePairProcess) controllableProcess;
                }
                if (controllableProcess instanceof TradePairStrategyNNProcess) {
                    tradePairNNProcess = (TradePairStrategyNNProcess) controllableProcess;
                }
                switch (action) {
                    case "REMOVE":
                        controllableProcess.doStop();
                        break;
                    case "BUY":
                        controllableProcess.doBuy();
                        break;
                    case "SELL":
                        controllableProcess.doSell();
                        break;
                    case "CANCEL":
                        controllableProcess.orderCancel();
                        break;
                    case "PLOT":
                        if (pairProcess == null) break;
                        pairProcess.doShowPlot();
                        break;
                    case "BROWSER":
                        if (pairProcess == null) break;
                        if (Desktop.isDesktopSupported()) {
                            try {
                                String url = "https://www.binance.com/en/trade/pro/"+pairProcess.getBaseSymbol()+"_"+pairProcess.getQuoteSymbol();
                                Desktop.getDesktop().browse(new URI(url));
                            } catch (URISyntaxException | IOException ex) {
                                Logger.getLogger(mainApplication.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        break;
                    case "STATISTICS":
                        if (pairProcess == null) break;
                        pairProcess.doShowStatistics();
                        break;
                    case "NNBTRAIN":
                        if (tradePairNNProcess == null) break;
                        tradePairNNProcess.doNetworkAction("TRAIN", "BASE");
                        break;
                    case "NNCTRAIN":
                        if (tradePairNNProcess == null) break;
                        tradePairNNProcess.doNetworkAction("TRAIN", "COIN");
                        break;
                    case "NNBADD":
                        if (tradePairNNProcess == null) break;
                        tradePairNNProcess.doNetworkAction("ADDSET", "BASE");
                        break;
                    case "NNCADD":
                        if (tradePairNNProcess == null) break;
                        tradePairNNProcess.doNetworkAction("ADDSET", "COIN");
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

    /**
     * @return the pyramidAutoMaxSize
     */
    public int getPyramidAutoMaxSize() {
        return pyramidAutoMaxSize;
    }

    /**
     * @param pyramidAutoMaxSize the pyramidAutoMaxSize to set
     */
    public void setPyramidAutoMaxSize(int pyramidAutoMaxSize) {
        this.pyramidAutoMaxSize = pyramidAutoMaxSize;
    }

    /**
     * @return the tradingBalanceMainValue
     */
    public double getTradingBalanceMainValue() {
        return tradingBalanceMainValue;
    }

    /**
     * @param tradingBalanceMainValue the tradingBalanceMainValue to set
     */
    public void setTradingBalanceMainValue(double tradingBalanceMainValue) {
        this.tradingBalanceMainValue = tradingBalanceMainValue;
    }

    /**
     * @return the wavesTradingBalancePercent
     */
    public double getWavesTradingBalancePercent() {
        return wavesTradingBalancePercent;
    }

    /**
     * @param wavesTradingBalancePercent the wavesTradingBalancePercent to set
     */
    public void setWavesTradingBalancePercent(double wavesTradingBalancePercent) {
        this.wavesTradingBalancePercent = wavesTradingBalancePercent;
    }

    /**
     * @return the wavesTradingBalanceMainValue
     */
    public double getWavesTradingBalanceMainValue() {
        return wavesTradingBalanceMainValue;
    }

    /**
     * @param wavesTradingBalanceMainValue the wavesTradingBalanceMainValue to set
     */
    public void setWavesTradingBalanceMainValue(double wavesTradingBalanceMainValue) {
        this.wavesTradingBalanceMainValue = wavesTradingBalanceMainValue;
    }

    /**
     * @return the wavesSecondaryPercent
     */
    public double getWavesSecondaryPercent() {
        return wavesSecondaryPercent;
    }

    /**
     * @param wavesSecondaryPercent the wavesSecondaryPercent to set
     */
    public void setWavesSecondaryPercent(double wavesSecondaryPercent) {
        this.wavesSecondaryPercent = wavesSecondaryPercent;
    }

    /**
     * @return the wavesIncKoef
     */
    public double getWavesIncKoef() {
        return wavesIncKoef;
    }

    /**
     * @param wavesIncKoef the wavesIncKoef to set
     */
    public void setWavesIncKoef(double wavesIncKoef) {
        this.wavesIncKoef = wavesIncKoef;
    }

    /**
     * @return the wavesInitialProfitPercent
     */
    public double getWavesInitialProfitPercent() {
        return wavesInitialProfitPercent;
    }

    /**
     * @param wavesInitialProfitPercent the wavesInitialProfitPercent to set
     */
    public void setWavesInitialProfitPercent(double wavesInitialProfitPercent) {
        this.wavesInitialProfitPercent = wavesInitialProfitPercent;
    }

    /**
     * @return the wavesMinProfitPercent
     */
    public double getWavesMinProfitPercent() {
        return wavesMinProfitPercent;
    }

    /**
     * @param wavesMinProfitPercent the wavesMinProfitPercent to set
     */
    public void setWavesMinProfitPercent(double wavesMinProfitPercent) {
        this.wavesMinProfitPercent = wavesMinProfitPercent;
    }

    /**
     * @return the wavesHalfDivideProfitOrdersCount
     */
    public double getWavesHalfDivideProfitOrdersCount() {
        return wavesHalfDivideProfitOrdersCount;
    }

    /**
     * @param wavesHalfDivideProfitOrdersCount the wavesHalfDivideProfitOrdersCount to set
     */
    public void setWavesHalfDivideProfitOrdersCount(double wavesHalfDivideProfitOrdersCount) {
        this.wavesHalfDivideProfitOrdersCount = wavesHalfDivideProfitOrdersCount;
    }
}
