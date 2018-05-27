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
import java.util.List;
import java.util.Map;

/**
 *
 * @author EVG_adm_T
 */
public class SignalOrderController extends RunProcessNoThread implements BaseAutoOrderControllerInterface{

    private final CoinRatingController coinRatingController;
    private SignalController signalcontroller = null;
    private final TradePairProcessList pairProcessList;
    private AutoPairOrders autoOrders = null;

    private boolean autoSignalOrder = false;
    private boolean autoSignalFastOrder = false;
    private float minSignalRatingForOrder = 4;
    private float minProfitPercentForOrder = 4;
    
    public SignalOrderController(CoinRatingController coinRatingController, TradePairProcessList pairProcessList) {
        this.coinRatingController = coinRatingController;
        signalcontroller = new SignalController();
        signalcontroller.setCoinRatingController(coinRatingController);
        this.pairProcessList = pairProcessList;
        autoOrders = coinRatingController.getAutoOrders();
    }

    @Override
    public void onAfterAutoOrdersExitCheck() {
        List<String> listRemove = new ArrayList<>();
        if (autoOrders.getEntered().size() > 0) {
            if (listRemove.isEmpty() && autoOrders.isMoreThanMaxEntered()) {
                String worst_free_pair = getWorstFreeEnteredSignal();
                if (worst_free_pair != null && !worst_free_pair.isEmpty()) {
                    mainApplication.getInstance().log("Exit from worst order: " + worst_free_pair, true, true);
                    pairProcessList.removePair(worst_free_pair);
                    listRemove.add(worst_free_pair);
                    autoOrders.getEntered().get(worst_free_pair).pair = null;
                }
            }
            listRemove.forEach((entry) -> {
                autoOrders.getEntered().remove(entry);
            });
        } else if (!autoOrders.isMaxEntered()) {
            SignalItem best_signal = signalcontroller.getBestSignalToReEnter(minSignalRatingForOrder);
            if (best_signal != null && best_signal.getMedianProfitPercent() > minProfitPercentForOrder) {
                if (!autoOrders.orderEnter(best_signal.getPair(), autoSignalFastOrder, true, best_signal, this::checkSignalExit)) {
                    best_signal.setAutopick(false);
                }
            }
        }
    }
    
    /*private boolean signalOrderEnter(SignalItem item) {
        String pair = item.getPair();
        if (
            autoSignalOrder && 
            !autoOrders.pairIsEntered(pair) && 
            !coinRatingController.getCoinPairRatingMap().isEmpty() &&
            item.getMedianProfitPercent() > 4
        ) {
            if (
                !pair.isEmpty() && 
                !pairProcessList.hasPair(pair)
            ) {
                CoinRatingPairLogItem toenter = coinRatingController.getCoinPairRatingMap().get(pair);
                if (toenter != null) {
                    if (autoSignalFastOrder) {
                        mainApplication.getInstance().log("Trying to auto fast-enter signal with pair: " + pair, true, true);
                        toenter.pair = pairProcessList.addPairFastRun(pair);
                    } else {
                        mainApplication.getInstance().log("Trying to auto enter signal with pair: " + pair, true, true);
                        toenter.pair = pairProcessList.addPair(pair);
                    }
                    if (toenter.pair instanceof TradeSignalProcessInterface) ((TradeSignalProcessInterface)toenter.pair).setSignalItem(item);
                    entered.put(pair, toenter);
                    doWait(1000);
                    return true;
                }
            }
        }
        return false;
    }*/
    
    private boolean checkSignalExit(CoinRatingPairLogItem rentered) {
        if (rentered != null && rentered.pair != null && rentered.pair.isInitialized() && rentered.pair instanceof TradeSignalProcessInterface) {
            boolean is_free = !rentered.pair.isInOrder() && 
                                !rentered.pair.isInAPIOrder() &&
                                rentered.pair.getFullOrdersCount() == 0;
            return (
                is_free &&
                rentered.pair.getLastPrice() != null &&
                rentered.pair.getLastPrice().compareTo(BigDecimal.ZERO) > 0 &&
                ((TradeSignalProcessInterface)rentered.pair).getSignalItem() != null &&
                ((TradeSignalProcessInterface)rentered.pair).getSignalItem().getPriceTarget().compareTo(rentered.pair.getLastPrice()) < 0
            ) || (
                is_free &&
                signalcontroller.getPairSignalRating(rentered.pair.getSymbol()) < minSignalRatingForOrder
            ) || (
                rentered.pair.getFullOrdersCount() > 0
            );
        }
        return false;
    }
    
    /*private void checkSignalOrders() {
        if (entered.size() > 0) {
            List<String> listRemove = new ArrayList<>();
            for (Map.Entry<String, CoinRatingPairLogItem> entry : entered.entrySet()) {
                CoinRatingPairLogItem rentered = entry.getValue();
                if (rentered != null && rentered.pair != null && rentered.pair.isInitialized() && rentered.pair instanceof TradeSignalProcessInterface) {
                    boolean is_free = !rentered.pair.isInOrder() && 
                                !rentered.pair.isInAPIOrder() &&
                                rentered.pair.getFullOrdersCount() == 0;
                    if (    (
                                is_free &&
                                (System.currentTimeMillis() - rentered.pair.getStartMillis()) > secondsOrderEnterWait * 1000
                            ) || (
                                is_free &&
                                rentered.pair.getLastPrice() != null &&
                                rentered.pair.getLastPrice().compareTo(BigDecimal.ZERO) > 0 &&
                                ((TradeSignalProcessInterface)rentered.pair).getSignalItem() != null &&
                                ((TradeSignalProcessInterface)rentered.pair).getSignalItem().getPriceTarget().compareTo(rentered.pair.getLastPrice()) < 0
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
                        pairProcessList.removePair(rentered.pair.getSymbol());
                        if (rentered.pair.getLastTradeProfit().compareTo(BigDecimal.ZERO) < 0 || !rentered.pair.isTriedBuy()) {
                            rentered.rating_inc -= 1;
                        } else {
                            rentered.rating_inc += 0.5;
                        }
                        rentered.calculateRating();
                        listRemove.add(rentered.pair.getSymbol());
                        if (((TradeSignalProcessInterface)rentered.pair).getSignalItem() != null && rentered.pair.getFullOrdersCount() > 0) {
                            ((TradeSignalProcessInterface)rentered.pair).getSignalItem().setAutopick(false);
                        }
                        rentered.pair = null;
                    }
                }
            }
            if (listRemove.isEmpty() && autoOrders.isMaxEntered()) {
                String worst_free_pair = getWorstFreeEnteredSignal();
                if (worst_free_pair != null && !worst_free_pair.isEmpty()) {
                    mainApplication.getInstance().log("Exit from worst order: " + worst_free_pair, true, true);
                    pairProcessList.removePair(worst_free_pair);
                    listRemove.add(worst_free_pair);
                    autoOrders.getEntered().get(worst_free_pair).pair = null;
                }
            }
            listRemove.forEach((entry) -> {
                autoOrders.getEntered().remove(entry);
            });
        } else if (!autoOrders.isMaxEntered()) {
            SignalItem best_signal = signalcontroller.getBestSignalToReEnter(minSignalRatingForOrder);
            if (best_signal != null && best_signal.getMedianProfitPercent() > 4) {
                if (!autoOrders.orderEnter(best_signal.getPair(), autoSignalFastOrder, true, best_signal, this::checkSignalExit)) {
                    best_signal.setAutopick(false);
                }
            }
        }
    }*/

    private String getWorstFreeEnteredSignal() {
        String result = null;
        double minCrit = 0;
        for (Map.Entry<String, CoinRatingPairLogItem> entry : autoOrders.getEntered().entrySet()) {
            CoinRatingPairLogItem rentered = entry.getValue();
            if (
                rentered != null && 
                rentered.pair != null && 
                rentered.pair.isInitialized() && 
                rentered.pair instanceof TradeSignalProcessInterface && 
                ((TradeSignalProcessInterface)rentered.pair).getSignalItem() != null
            ) {
                boolean is_free = !rentered.pair.isInOrder()
                        && !rentered.pair.isInAPIOrder()
                        && rentered.pair.getFullOrdersCount() == 0;
                if (is_free) {
                    double currCrit = ((TradeSignalProcessInterface)rentered.pair).getSignalItem().getCurrentRating()*((TradeSignalProcessInterface)rentered.pair).getSignalItem().getPriceProfitPercent(rentered.pair.getLastPrice());
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
        autoOrders.registerController(this);
        signalcontroller.setSignalEvent((item, rating) -> {
            if (coinRatingController.setPairSignalRating(item.getPair(), (float) rating)) {
                if (
                    signalcontroller.isInitialSignalsLoaded() &&
                    rating >= minSignalRatingForOrder && 
                    item.getCurrentRating() > minSignalRatingForOrder/2 && 
                    !item.isDone() && 
                    !item.isTimeout() && 
                    item.getMillisFromSignalStart() >= 0 &&
                    item.getMillisFromSignalStart() < 2 * 60 * 60 * 1000 &&
                    item.getMedianProfitPercent() > minProfitPercentForOrder
                ) {
                    autoOrders.orderEnter(item.getPair(), autoSignalFastOrder, true, item, this::checkSignalExit);
                }
            }
        });
        signalcontroller.startSignalsProcess();
    }

    @Override
    protected void runFinish() {
        autoOrders.unregisterController(this);
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
    
    public void setClient(TradingAPIAbstractInterface _client) {
        signalcontroller.setClient(_client);
    }
}
