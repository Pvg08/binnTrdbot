/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.coinrating.CoinRatingController;
import com.evgcompany.binntrdbot.coinrating.CoinRatingPairLogItem;
import com.evgcompany.binntrdbot.events.AutoOrderExitCheck;
import com.evgcompany.binntrdbot.signal.SignalItem;
import com.evgcompany.binntrdbot.signal.TradeSignalProcessInterface;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author EVG_adm_T
 */
public class AutoPairOrders {
    
    private final CoinRatingController coinRatingController;
    private final TradePairProcessList pairProcessList;
    
    private final Map<String, CoinRatingPairLogItem> entered = new HashMap<>();
    private final List<BaseAutoOrderControllerInterface> controllers = new ArrayList<>();
    private int maxEnter = 10;
    private int secondsOrderEnterWait = 28800;

    public AutoPairOrders(CoinRatingController coinRatingController, TradePairProcessList pairProcessList) {
        this.coinRatingController = coinRatingController;
        this.pairProcessList = pairProcessList;
    }
    
    public void registerController(BaseAutoOrderControllerInterface controller) {
        if (!controllers.contains(controller)) {
            controllers.add(controller);
        }
    }
    public void unregisterController(BaseAutoOrderControllerInterface controller) {
        if (controllers.contains(controller)) {
            controllers.remove(controller);
        }
    }
    
    public boolean pairIsEntered(String pair) {
        return entered.containsKey(pair);
    }
    public boolean isMaxEntered() {
        return entered.size() >= maxEnter;
    }
    public boolean isMoreThanMaxEntered() {
        return entered.size() > maxEnter;
    }
    
    public boolean orderEnter(String pair, boolean isFast, boolean isIgnoreMaxCount, SignalItem signalItem, AutoOrderExitCheck exit_checker) {
        if (
            (isIgnoreMaxCount || entered.size() < maxEnter) && 
            !entered.containsKey(pair) && 
            !coinRatingController.getCoinPairRatingMap().isEmpty()
        ) {
            if (!pair.isEmpty() && !pairProcessList.hasPair(pair)) {
                CoinRatingPairLogItem toenter = coinRatingController.getCoinPairRatingMap().get(pair);
                if (toenter != null) {
                    if (isFast) {
                        mainApplication.getInstance().log("Trying to auto fast-enter"+(signalItem!=null ? " signal" : "")+" with pair: " + pair, true, true);
                        toenter.pair = pairProcessList.addPairFastRun(pair);
                    } else {
                        mainApplication.getInstance().log("Trying to auto enter"+(signalItem!=null ? " signal" : "")+" with pair: " + pair, true, true);
                        toenter.pair = pairProcessList.addPair(pair);
                    }
                    if (toenter.pair instanceof TradeSignalProcessInterface) {
                        ((TradeSignalProcessInterface)toenter.pair).setSignalItem(signalItem);
                    }
                    toenter.exit_check = exit_checker;
                    entered.put(pair, toenter);
                    return true;
                }
            }
        }
        return false;
    }
    
    public void checkOrderExit() {
        if (entered.size() > 0) {
            List<String> listRemove = new ArrayList<>();
            for (Map.Entry<String, CoinRatingPairLogItem> entry : entered.entrySet()) {
                CoinRatingPairLogItem rentered = entry.getValue();
                if (rentered != null && rentered.pair != null && rentered.pair.isInitialized()) {
                    boolean is_free = !rentered.pair.isInOrder() && 
                                !rentered.pair.isInAPIOrder() &&
                                rentered.pair.getFullOrdersCount() == 0;
                    if (    (
                                is_free && 
                                (System.currentTimeMillis() - rentered.pair.getStartMillis()) > secondsOrderEnterWait * 1000
                            ) || (
                                rentered.pair.isTriedBuy() && 
                                !rentered.pair.isInOrder() && 
                                !rentered.pair.isInAPIOrder()
                            ) || (
                                !rentered.pair.isAlive()
                            ) || (
                                rentered.exit_check != null &&
                                rentered.exit_check.isNeedExit(rentered)
                            )
                    ) {
                        boolean is_signal = rentered.pair instanceof TradeSignalProcessInterface && ((TradeSignalProcessInterface)rentered.pair).getSignalItem() != null;
                        mainApplication.getInstance().log("Exit from"+(is_signal ? " signal" : "")+" order: " + rentered.pair.getSymbol(), true, true);
                        if (rentered.pair.isAlive()) rentered.pair.doStop();
                        if ((rentered.pair.getLastTradeProfit() != null && rentered.pair.getLastTradeProfit().compareTo(BigDecimal.ZERO) < 0) || 
                            !rentered.pair.isTriedBuy()
                        ) {
                            //rentered.fastbuy_skip = true;
                            rentered.rating_inc -= 1;
                        } else {
                            rentered.rating_inc += 0.5;
                        }
                        rentered.calculateRating();
                        listRemove.add(rentered.pair.getSymbol());
                        if (is_signal && rentered.pair.getFullOrdersCount() > 0) {
                            ((TradeSignalProcessInterface)rentered.pair).getSignalItem().setAutopick(false);
                        }
                        rentered.pair = null;
                    }
                }
            }
            listRemove.forEach((entry) -> {
                entered.remove(entry);
            });
            controllers.forEach((controller) -> {
                controller.onAfterAutoOrdersExitCheck();
            });
        }
    }
    
    /*@Override
    protected void runStart() {
        
    }

    @Override
    protected void runBody() {
        checkOrderExit();
    }

    @Override
    protected void runFinish() {
        
    }*/
    
    /**
     * @return the entered
     */
    public Map<String, CoinRatingPairLogItem> getEntered() {
        return entered;
    }

    /**
     * @return the maxEnter
     */
    public int getMaxEnter() {
        return maxEnter;
    }

    /**
     * @param maxEnter the maxEnter to set
     */
    public void setMaxEnter(int maxEnter) {
        this.maxEnter = maxEnter;
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
}
