/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.signal;

import com.evgcompany.binntrdbot.*;
import com.evgcompany.binntrdbot.coinrating.*;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author EVG_adm_T
 */
public class SignalOrderController extends PeriodicProcessThread {

    private final CoinRatingController coinRatingController;
    private SignalController signalcontroller = null;
    private final tradePairProcessController paircontroller;

    private boolean autoSignalOrder = false;
    private boolean autoSignalFastOrder = false;
    private float minSignalRatingForOrder = 4;
    
    private final Map<String, CoinRatingPairLogItem> entered = new HashMap<>();
    private int maxEnter = 10;
    private int secondsOrderEnterWait = 28800;
    
    public SignalOrderController(CoinRatingController coinRatingController, tradePairProcessController paircontroller) {
        this.coinRatingController = coinRatingController;
        signalcontroller = new SignalController();
        signalcontroller.setCoinRatingController(coinRatingController);
        this.paircontroller = paircontroller;
    }

    private boolean signalOrderEnter(SignalItem item) {
        String pair = item.getPair();
        if (
            autoSignalOrder && 
            !coinRatingController.getEntered().containsKey(pair) && 
            !entered.containsKey(pair) && 
            !coinRatingController.getCoinRatingMap().isEmpty() &&
            item.getMedianProfitPercent() > 4
        ) {
            if (
                !pair.isEmpty() && 
                !paircontroller.hasPair(pair)
            ) {
                CoinRatingPairLogItem toenter = coinRatingController.getCoinRatingMap().get(pair);
                if (toenter != null) {
                    if (autoSignalFastOrder) {
                        mainApplication.getInstance().log("Trying to auto fast-enter signal with pair: " + pair, true, true);
                        toenter.pair = paircontroller.addPairFastRun(pair);
                    } else {
                        mainApplication.getInstance().log("Trying to auto enter signal with pair: " + pair, true, true);
                        toenter.pair = paircontroller.addPair(pair);
                    }
                    toenter.pair.setSignalOrder(item);
                    entered.put(pair, toenter);
                    doWait(1000);
                    return true;
                }
            }
        }
        return false;
    }

    private void checkSignalOrders() {
        if (entered.size() > 0) {
            List<String> listRemove = new ArrayList<>();
            for (Map.Entry<String, CoinRatingPairLogItem> entry : entered.entrySet()) {
                CoinRatingPairLogItem rentered = entry.getValue();
                if (rentered != null && rentered.pair != null && rentered.pair.isInitialized()) {
                    boolean is_free = !rentered.pair.isHodling() && 
                                !rentered.pair.isInLimitOrder() &&
                                rentered.pair.getFullOrdersCount() == 0;
                    if (    (
                                is_free &&
                                (System.currentTimeMillis() - rentered.pair.getStartMillis()) > secondsOrderEnterWait * 1000
                            ) || (
                                is_free &&
                                rentered.pair.getLastPrice() != null &&
                                rentered.pair.getLastPrice().compareTo(BigDecimal.ZERO) > 0 &&
                                rentered.pair.getSignalItem() != null &&
                                rentered.pair.getSignalItem().getPriceTarget().compareTo(rentered.pair.getLastPrice()) < 0
                            ) || (
                                is_free &&
                                signalcontroller.getPairSignalRating(rentered.pair.getSymbol()) < minSignalRatingForOrder
                            ) || (
                                rentered.pair.getFullOrdersCount() > 0
                            ) || (
                                !rentered.pair.isAlive()
                            )
                    ) {
                        mainApplication.getInstance().log("Exit from order: " + rentered.pair.getSymbol(), true, true);
                        paircontroller.removePair(rentered.pair.getSymbol());
                        if (rentered.pair.getLastTradeProfit().compareTo(BigDecimal.ZERO) < 0 || !rentered.pair.isTriedBuy()) {
                            rentered.rating_inc -= 1;
                        } else {
                            rentered.rating_inc += 0.5;
                        }
                        rentered.calculateRating();
                        listRemove.add(rentered.pair.getSymbol());
                        if (rentered.pair.getSignalItem() != null && rentered.pair.getFullOrdersCount() > 0) {
                            rentered.pair.getSignalItem().setAutopick(false);
                        }
                        rentered.pair = null;
                    }
                }
            }
            if (listRemove.isEmpty() && entered.size() > maxEnter) {
                String worst_free_pair = getWorstFreeEnteredSignal();
                if (worst_free_pair != null && !worst_free_pair.isEmpty()) {
                    mainApplication.getInstance().log("Exit from worst order: " + worst_free_pair, true, true);
                    paircontroller.removePair(worst_free_pair);
                    listRemove.add(worst_free_pair);
                    entered.get(worst_free_pair).pair = null;
                }
            }
            listRemove.forEach((entry) -> {
                entered.remove(entry);
            });
        } else if (entered.size() < maxEnter) {
            SignalItem best_signal = signalcontroller.getBestSignalToReEnter(minSignalRatingForOrder);
            if (best_signal != null) {
                if (!signalOrderEnter(best_signal)) {
                    best_signal.setAutopick(false);
                }
            }
        }
    }

    private String getWorstFreeEnteredSignal() {
        String result = null;
        double minCrit = 0;
        for (Map.Entry<String, CoinRatingPairLogItem> entry : entered.entrySet()) {
            CoinRatingPairLogItem rentered = entry.getValue();
            if (rentered != null && rentered.pair != null && rentered.pair.isInitialized() && rentered.pair.getSignalItem() != null) {
                boolean is_free = !rentered.pair.isHodling()
                        && !rentered.pair.isInLimitOrder()
                        && rentered.pair.getFullOrdersCount() == 0;
                if (is_free) {
                    double currCrit = rentered.pair.getSignalItem().getCurrentRating()*rentered.pair.getSignalItem().getPriceProfitPercent(rentered.pair.getLastPrice());
                    if (result == null || minCrit > currCrit) {
                        result = entry.getKey();
                        minCrit = currCrit;
                    }
                }
            }
        }
        return result;
    }
    
    @Override
    protected void runStart() {
        mainApplication.getInstance().log("SignalOrderController starting...");
        signalcontroller.setSignalEvent((item, rating) -> {
            if (coinRatingController.setPairSignalRating(item.getPair(), (float) rating)) {
                if (
                        signalcontroller.isInitialSignalsLoaded() &&
                        rating >= minSignalRatingForOrder && 
                        item.getCurrentRating() > minSignalRatingForOrder/2 && 
                        !item.isDone() && 
                        !item.isTimeout() && 
                        item.getMillisFromSignalStart() >= 0 &&
                        item.getMillisFromSignalStart() < 2 * 60 * 60 * 1000
                ) {
                    signalOrderEnter(item);
                }
            }
        });
        signalcontroller.startSignalsProcess();
    }

    @Override
    protected void runBody() {
        checkSignalOrders();
    }

    @Override
    protected void runFinish() {
        signalcontroller.stopSignalsProcessIfRunning();
        mainApplication.getInstance().log("SignalOrderController finished.");
    }
    
    public boolean isInitialSignalsLoaded() {
        return signalcontroller.isInitialSignalsLoaded();
    }
    
    /**
     * @return the signalcontroller
     */
    public SignalController getSignalController() {
        return signalcontroller;
    }
    
    /**
     * @return the autoSignalOrder
     */
    public boolean isAutoSignalOrder() {
        return autoSignalOrder;
    }

    /**
     * @param autoSignalOrder the autoSignalOrder to set
     */
    public void setAutoSignalOrder(boolean autoSignalOrder) {
        this.autoSignalOrder = autoSignalOrder;
    }

    /**
     * @return the autoSignalFastOrder
     */
    public boolean isAutoSignalFastOrder() {
        return autoSignalFastOrder;
    }

    /**
     * @param autoSignalFastOrder the autoSignalFastOrder to set
     */
    public void setAutoSignalFastOrder(boolean autoSignalFastOrder) {
        this.autoSignalFastOrder = autoSignalFastOrder;
    }
    
    /**
     * @return the minSignalRatingForOrder
     */
    public float getMinSignalRatingForOrder() {
        return minSignalRatingForOrder;
    }

    /**
     * @param minSignalRatingForOrder the minSignalRatingForOrder to set
     */
    public void setMinSignalRatingForOrder(float minSignalRatingForOrder) {
        this.minSignalRatingForOrder = minSignalRatingForOrder;
    }
    
    /**
     * @return the secondsOrderEnterWait
     */
    public int getSecondsOrderEnterWait() {
        return secondsOrderEnterWait;
    }

    /**
     * @param secondsOrderEnterWait the secondsOrderEnterWait to set
     */
    public void setSecondsOrderEnterWait(int secondsOrderEnterWait) {
        this.secondsOrderEnterWait = secondsOrderEnterWait;
    }
    
    public void setClient(TradingAPIAbstractInterface _client) {
        signalcontroller.setClient(_client);
    }
    
    /**
     * @param maxEnter the maxEnter to set
     */
    public void setMaxEnter(int maxEnter) {
        this.maxEnter = maxEnter;
    }

    public Map<String, CoinRatingPairLogItem> getEntered() {
        return entered;
    }
}
