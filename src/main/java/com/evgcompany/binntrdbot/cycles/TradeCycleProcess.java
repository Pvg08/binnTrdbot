/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.cycles;

import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.evgcompany.binntrdbot.BalanceController;
import com.evgcompany.binntrdbot.CoinFilters;
import com.evgcompany.binntrdbot.ControllableOrderProcess;
import com.evgcompany.binntrdbot.OrdersController;
import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.OrderPairItem;
import com.evgcompany.binntrdbot.mainApplication;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author EVG_adm_T
 */
public class TradeCycleProcess extends PeriodicProcessThread implements ControllableOrderProcess {
    
    TradingAPIAbstractInterface client = null;
    CoinCycleController cycleController = null;

    private int cycleIndex = 0; 
    
    private String pairAssetSymbol;
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    private String mainAsset;

    private boolean isBuy;
    private CoinFilters filter = null;
    private CoinInfoAggregator info = null;

    private final List<String> cycle = new ArrayList<>();
    private final List<BigDecimal> prices = new ArrayList<>();
    private final Map<String, BigDecimal> cycleCoins = new HashMap<>();
    private final Map<String, Long> orderCIDs = new HashMap<>();
    private final Set<String> usedPairs = new HashSet<>();
    private int cycleStep;

    private int switchesCount = 0;
    private OrdersController ordersController = null;
    private long startMillis = 0;
    private long lastOrderMillis = 0;
    private long lastPriceMillis = 0;
    
    private BigDecimal current_order_amount;
    private BigDecimal current_order_price;
    
    private BigDecimal initialQty = null;
    private String initialAsset = null;
    private double profitAim;
    
    private boolean reverting = false;
    private boolean nextDoAbort = false;
    private boolean inAPIOrder = false;

    public TradeCycleProcess(CoinCycleController cycleController) {
        this.cycleController = cycleController;
        client = cycleController.getClient();
        ordersController = OrdersController.getInstance();
        startMillis = System.currentTimeMillis();
        isBuy = false;
        mainAsset = "";
        info = CoinInfoAggregator.getInstance();
    }

    public void addChain(String pair, BigDecimal price) {
        usedPairs.add(getChainPair(pair));
        cycle.add(pair);
        prices.add(price);
    }
    public void addChain(String pair, double price) {
        addChain(pair, BigDecimal.valueOf(price));
    }
    public void cutChain() {
        for(int i = cycle.size()-1; i >= cycleStep; i--) {
            String pair = getChainPair(cycle.get(i));
            ordersController.unregisterPairTrade(orderCIDs.get(pair));
            orderCIDs.remove(pair);
            usedPairs.remove(pair);
            cycle.remove(i);
            prices.remove(i);
        }
    }
    public void addCycleToCycleChain(List<String> new_cycle, int from) {
        for(int i=from; i < new_cycle.size(); i++) {
            String symbol1 = new_cycle.get(i-1);
            String symbol2 = new_cycle.get(i);
            if (info.getPairsGraph().containsEdge(symbol2, symbol1)) { // BUY
                addChain(symbol2+symbol1 + " BUY", cycleController.getEdgeWeight(symbol2, symbol1)); 
            } else { // SELL
                addChain(symbol1+symbol2 + " SELL", cycleController.getEdgeWeight(symbol1, symbol2));
            }
        }
    }
    public void addCycleToCycleChain(List<String> new_cycle) {
        addCycleToCycleChain(new_cycle, 1);
    }
    
    private boolean chainIsBuy(String chain) {
        return chain.matches("^.* BUY$");
    }
    private String getChainPair(String chain) {
        return chain.substring(0, chain.indexOf(" ")).trim();
    }

    private void doPreStep(int from) {
        for(int i = from; i<cycle.size(); i++) {
            String cpair = getChainPair(cycle.get(i));
            Long orderCID = ordersController.registerPairTrade(cpair, this, this::onOrderEvent);
            orderCIDs.put(cpair, orderCID);
        }
        updateOrderPairPrices();
    }

    private void doPostStep() {
        orderCIDs.forEach((pair, orderCID)->{
            ordersController.unregisterPairTrade(orderCID);
        });
    }
    
    private void doSwitchStep() {
        if (switchesCount >= cycleController.getMaxSwitchesCount()) {
            return;
        }
        if (cycleController.doCycleSwap(this)) {
            mainApplication.getInstance().log("We wait too long. Need to switch "+pairAssetSymbol+"'s cycle order...");
            if (inAPIOrder) {
                Order order = ordersController.getAPIOrder(orderCIDs.get(pairAssetSymbol));
                if (order.getStatus() != OrderStatus.NEW) return;
                ordersController.cancelOrder(orderCIDs.get(pairAssetSymbol));
                doWait(1000);
            }
            inAPIOrder = false;
            ordersController.finishOrder(orderCIDs.get(pairAssetSymbol), false, null);
            switchesCount++;
            doPreStep(cycleStep);
            initAndOrderStep();
        }
    }
    
    private void initAndOrderStep() {
        updateOrderPairPrices();
        lastOrderMillis = System.currentTimeMillis();
        pairAssetSymbol = cycle.get(cycleStep);
        isBuy = chainIsBuy(pairAssetSymbol);
        pairAssetSymbol = getChainPair(pairAssetSymbol);
        filter = info.getPairFilters().get(pairAssetSymbol);
        baseAssetSymbol = filter.getBaseAssetSymbol();
        quoteAssetSymbol = filter.getQuoteAssetSymbol();
        if (mainAsset.isEmpty()) {
            mainAsset = isBuy ? quoteAssetSymbol : baseAssetSymbol;
        }
        pairAssetSymbol = baseAssetSymbol+quoteAssetSymbol;
        
        current_order_price = prices.get(cycleStep);
        ordersController.setPairOrderPrice(orderCIDs.get(pairAssetSymbol), current_order_price);
        if (cycleStep == 0) {
            if (isBuy) {
                current_order_amount = BalanceController.getInstance().getOrderAssetAmount(quoteAssetSymbol, cycleController.getTradingBalancePercent());
                cycleCoins.put(quoteAssetSymbol, current_order_amount.stripTrailingZeros());
            } else {
                current_order_amount = BalanceController.getInstance().getOrderAssetAmount(baseAssetSymbol, cycleController.getTradingBalancePercent());
                cycleCoins.put(baseAssetSymbol, current_order_amount.stripTrailingZeros());
            }
        } else {
            if (isBuy) {
                current_order_amount = cycleCoins.get(quoteAssetSymbol);
            } else {
                current_order_amount = cycleCoins.get(baseAssetSymbol);
            }
        }
        if (!isBuy) {
            ordersController.PreBuy(orderCIDs.get(pairAssetSymbol), current_order_amount, current_order_price);
        }
        lastPriceMillis = 0;
        doOrder(baseAssetSymbol, quoteAssetSymbol, isBuy, false, current_order_price, current_order_amount);
    }
    
    private void doNextStep() {
        if (nextDoAbort) {
            orderAbort();
            return;
        }
        if (need_stop) {
            return;
        }
        if (inAPIOrder) {
            return;
        }
        if (cycleStep >= 0 && isBuy) {
            ordersController.finishOrder(orderCIDs.get(pairAssetSymbol), true, current_order_price);
            ordersController.updateAllPairTexts(true);
        }
        cycleStep++;
        if (cycleStep >= cycle.size()) {
            lastPriceMillis = 0;
            updateOrderPairPrices();
            doStop();
            mainApplication.getInstance().log("Cycle "+cycle+" is finished!", true, true);
            return;
        }
        initAndOrderStep();
        if (cycleStep == 0) {
            setDelayTime(2);
        }
    }
    
    private void orderAbort() {
        if (inAPIOrder) {
            ordersController.cancelOrder(orderCIDs.get(pairAssetSymbol));
            doWait(1000);
        }
        ordersController.finishOrder(orderCIDs.get(pairAssetSymbol), false, null);
        inAPIOrder = false;
        lastPriceMillis = 0;
        
        revertCoinCycle();
        
        BalanceController.getInstance().updateAllBalances();
        doStop();
        mainApplication.getInstance().log("Cycle "+cycle+" is aborted!", true, true);
    }
    
    private void revertCoinCycle() {
        
        reverting = true;

        String successCoin = isBuy ? baseAssetSymbol : quoteAssetSymbol;
        String failureCoin = isBuy ? quoteAssetSymbol : baseAssetSymbol;

        BigDecimal successPart = cycleCoins.get(successCoin);
        BigDecimal failurePart = cycleCoins.get(failureCoin);
        
        List<String> successRevertPath = cycleController.getRevertPathDescription(successCoin, mainAsset);
        List<String> failureRevertPath = cycleController.getRevertPathDescription(failureCoin, mainAsset);
        
        if (successPart != null && successRevertPath != null && successPart.compareTo(BigDecimal.ZERO) > 0) {
            doRevertOrder(successPart, successRevertPath);
        }
        if (failurePart != null && failureRevertPath != null && failurePart.compareTo(BigDecimal.ZERO) > 0) {
            doRevertOrder(failurePart, failureRevertPath);
        }
        
        ordersController.finishOrder(orderCIDs.get(pairAssetSymbol), true, null);
        ordersController.updateAllPairTexts(true);
    }
    
    private void doRevertOrder(BigDecimal amount, List<String> path) {
        mainApplication.getInstance().log("Reverting " + amount + " " + path, true, true);
        if (path.isEmpty()) return;
        if (path.size() > 1) {
            // @todo
        }
        if (cycleStep >= 0 && isBuy) {
            ordersController.finishOrder(orderCIDs.get(pairAssetSymbol), true, null);
            ordersController.updateAllPairTexts(true);
        }
        pairAssetSymbol = path.get(0);
        isBuy = chainIsBuy(pairAssetSymbol);
        pairAssetSymbol = getChainPair(pairAssetSymbol);
        filter = new CoinFilters(pairAssetSymbol, client);
        filter.logFiltersInfo();

        Long orderCID = ordersController.registerPairTrade(pairAssetSymbol, this, this::onOrderEvent);
        orderCIDs.put(pairAssetSymbol, orderCID);

        baseAssetSymbol = filter.getBaseAssetSymbol();
        quoteAssetSymbol = filter.getQuoteAssetSymbol();
        current_order_amount = amount;
        if (info.getLastPrices().containsKey(pairAssetSymbol)) {
            current_order_price = BigDecimal.valueOf(info.getLastPrices().get(pairAssetSymbol));
        }
        if (!isBuy) {
            ordersController.PreBuy(orderCIDs.get(pairAssetSymbol), current_order_amount, current_order_price);
        }
        doOrder(baseAssetSymbol, quoteAssetSymbol, isBuy, true, current_order_price, current_order_amount);
        doWait(2000);
    }
    
    private void onOrderEvent(
            Long pairOrderCID, 
            OrderPairItem orderPair, 
            boolean isNew, 
            boolean isBuying, 
            boolean isCancelled, 
            boolean isFinished, 
            BigDecimal price, 
            BigDecimal qty, 
            BigDecimal executedQty) 
    {
        if (isNew) {
            inAPIOrder = true;
            lastOrderMillis = System.currentTimeMillis();
            if (isBuying) {
                mainApplication.getInstance().log("Buying "+orderPair.getSymbolPair()+"...", true, true);
            } else {
                mainApplication.getInstance().log("Selling "+orderPair.getSymbolPair()+"...", true, true);
            }
        }
        
        String orderCoins[] = info.getCoinPairs().get(orderPair.getSymbolPair());
        if (!isBuying) {
            cycleCoins.put(orderCoins[0], qty.subtract(executedQty).stripTrailingZeros());
            cycleCoins.put(orderCoins[1], executedQty.multiply(price).stripTrailingZeros());
        } else {
            cycleCoins.put(orderCoins[0], executedQty.stripTrailingZeros());
            cycleCoins.put(orderCoins[1], qty.subtract(executedQty).multiply(price).stripTrailingZeros());
        }
        
        if (!isFinished) {
            return;
        }
        inAPIOrder = false;
        
        if (isCancelled || executedQty.compareTo(qty) < 0) {
            // just cancelled or partially filled and then cancelled
            if (!reverting) nextDoAbort = true;
        } else {
            // filled
            lastPriceMillis = 0;
            ordersController.finishOrder(orderCIDs.get(pairAssetSymbol), true, null);
        }
    }
    
    private void checkOrders() {
        if (need_stop || !inAPIOrder) {
            return;
        }
        Order order = ordersController.getAPIOrder(orderCIDs.get(pairAssetSymbol));
        if (order == null || order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
            return;
        }

        System.out.println(order);
        
        if (cycleStep >= 1 && 
                Double.parseDouble(order.getExecutedQty()) <= 0 && 
                cycleController.getAbortMarketApproxProfit(initialQty, new BigDecimal(order.getOrigQty()), initialAsset, baseAssetSymbol, quoteAssetSymbol) > profitAim
        ) {
            mainApplication.getInstance().log("We'll have more profit if abort this "+pairAssetSymbol+" cycle order...");
            if (!reverting) orderAbort();
        } else {
            if (cycleController.isUseStopLimited() && (
                    (cycleStep >= 1 && (System.currentTimeMillis()-lastOrderMillis) > cycleController.getStopLimitTimeout() * 1000) ||
                    (cycleStep == 0 && Double.parseDouble(order.getExecutedQty()) <= 0 && (System.currentTimeMillis()-lastOrderMillis) > cycleController.getStopFirstLimitTimeout() * 1000)
                )
            ) {
                mainApplication.getInstance().log("We wait too long. Need to stop this "+pairAssetSymbol+"'s cycle order...");
                if (!reverting) orderAbort();
            } else if (cycleStep >= 1 && 
                    cycleStep < (cycle.size() - 1) && 
                    Double.parseDouble(order.getExecutedQty()) <= 0 && 
                    (System.currentTimeMillis()-lastOrderMillis) > cycleController.getSwitchLimitTimeout() * 1000
            ) {
                if (!reverting) doSwitchStep();
            }
        }
    }
    
    private void doOrder(String baseSymbol, String quoteSymbol, boolean isBuying, boolean isMarket, BigDecimal price, BigDecimal amount) {
        String symbol_order = baseSymbol+quoteSymbol;
        if (isBuying) {
            filter.setCurrentPrice(price);
            filter.setCurrentAmount(amount);
            filter.prepareForBuy();
            BigDecimal tobuy_price = filter.getCurrentPrice();
            BigDecimal tobuy_amount = filter.getCurrentAmount();
            if (BalanceController.getInstance().canBuy(symbol_order, tobuy_amount, tobuy_price)) {
                
                if (initialQty == null && initialAsset == null) {
                    initialAsset = quoteSymbol;
                    initialQty = tobuy_amount.multiply(tobuy_price);
                }
                
                mainApplication.getInstance().log("BYING " + NumberFormatter.df8.format(tobuy_amount) + " " + baseSymbol + " for " + NumberFormatter.df8.format(tobuy_amount.multiply(tobuy_price)) + " " + quoteSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")", true, true);
                long result = ordersController.Buy(orderCIDs.get(symbol_order), tobuy_amount, isMarket ? null : tobuy_price);
                if (result < 0) {
                    mainApplication.getInstance().log("Error!", true, true);
                    if (!reverting) orderAbort();
                }
            } else {
                BigDecimal avail = BalanceController.getInstance().getAvailableCount(quoteSymbol);
                if (avail == null) avail = BigDecimal.valueOf(-1);
                mainApplication.getInstance().log("Can't buy " + NumberFormatter.df8.format(tobuy_amount) + " " + baseSymbol + " for " + NumberFormatter.df8.format(tobuy_amount.multiply(tobuy_price)) + " " + quoteSymbol + " (price=" + NumberFormatter.df8.format(tobuy_price) + ")" + " (avail summ = "+avail+")");
                if (!reverting) orderAbort();
            }
        } else {
            filter.setCurrentPrice(price);
            filter.setCurrentAmount(amount);
            filter.prepareForSell();
            BigDecimal tosell_price = filter.getCurrentPrice();
            BigDecimal tosell_amount = filter.getCurrentAmount();
            if (BalanceController.getInstance().canSell(symbol_order, tosell_amount)) {
                
                if (initialQty == null && initialAsset == null) {
                    initialAsset = baseSymbol;
                    initialQty = tosell_amount;
                }
                
                mainApplication.getInstance().log("SELLING " + NumberFormatter.df8.format(tosell_amount) + " " + baseSymbol + " for " + NumberFormatter.df8.format(tosell_amount.multiply(tosell_price)) + " " + quoteSymbol + " (price=" + NumberFormatter.df8.format(tosell_price) + ")", true, true);
                long result = ordersController.Sell(orderCIDs.get(symbol_order), tosell_amount, isMarket ? null : tosell_price, null);
                if (result < 0) {
                    mainApplication.getInstance().log("Error!", true, true);
                    if (!reverting) orderAbort();
                }
            } else {
                BigDecimal avail = BalanceController.getInstance().getAvailableCount(baseSymbol);
                if (avail == null) avail = BigDecimal.valueOf(-1);
                mainApplication.getInstance().log("Can't sell " + tosell_amount + " " + symbol_order + " (avail summ = "+avail+")", false, true);
                if (!reverting) orderAbort();
            }
        }
    }
    
    private void updateOrderPairPrices(int from) {
        if (from < 0 || from > cycle.size()) {
            return;
        }
        if (lastPriceMillis == info.getPricesUpdateTimeMillis()) {
            return;
        }
        lastPriceMillis = info.getPricesUpdateTimeMillis();
        for(int i = 0; i < from; i++) {
            String cpair = getChainPair(cycle.get(i));
            OrderPairItem item = ordersController.getPairOrderInfo(orderCIDs.get(cpair));
            if (item != null) {
                item.setLastOrderPrice(null);
                item.setMarker("CYCLE" + cycleIndex + " PAIR" + (i+1) + " CLOSED");
                ordersController.updatePairTrade(orderCIDs.get(cpair), false);
            }
        }
        for(int i = from; i < cycle.size(); i++) {
            String cpair = getChainPair(cycle.get(i));
            OrderPairItem item = ordersController.getPairOrderInfo(orderCIDs.get(cpair));
            if (item != null) {
                item.setLastOrderPrice(prices.get(i));
                item.setMarker("CYCLE" + cycleIndex + " PAIR" + (i+1) + (cycleStep == i ? " ACTIVE" : " WAIT"));
                ordersController.updatePairTrade(orderCIDs.get(cpair), false);
            }
        }
    }
    private void updateOrderPairPrices() {
        lastPriceMillis = 0;
        updateOrderPairPrices(cycleStep);
    }
    
    @Override
    protected void runStart() {
        info.StartAndWaitForInit();
        inAPIOrder = false;
        cycleStep = -1;
        lastPriceMillis = 0;

        mainApplication.getInstance().log("thread for cycle " + cycle + " running...");
        mainApplication.getInstance().log("Prices: " + prices);
        mainApplication.getInstance().log("");

        doPreStep(0);
        updateOrderPairPrices(0);
    }
    
    @Override
    protected void runBody() {
        doNextStep();
        checkOrders();
        updateOrderPairPrices(cycleStep);
    }

    @Override
    protected void runFinish() {
        doPostStep();
        mainApplication.getInstance().log("thread for cycle " + cycle + " is stopped...");
    }

    public String getBaseSymbol() {
        return baseAssetSymbol;
    }

    public String getQuoteSymbol() {
        return quoteAssetSymbol;
    }

    public boolean isInLimitOrder() {
        return inAPIOrder;
    }

    /**
     * @return the startMillis
     */
    public long getStartMillis() {
        return startMillis;
    }

    /**
     * @return the profitAim
     */
    public double getProfitAim() {
        return profitAim;
    }

    /**
     * @param profitAim the profitAim to set
     */
    public void setProfitAim(double profitAim) {
        this.profitAim = profitAim;
    }

    /**
     * @return the cycle
     */
    public List<String> getCycle() {
        return cycle;
    }

    /**
     * @return the prices
     */
    public List<BigDecimal> getPrices() {
        return prices;
    }

    /**
     * @return the cycleStep
     */
    public int getCycleStep() {
        return cycleStep;
    }

    /**
     * @return the cycleCoins
     */
    public Map<String, BigDecimal> getCycleCoins() {
        return cycleCoins;
    }

    /**
     * @return the usedPairs
     */
    public Set<String> getUsedPairs() {
        return usedPairs;
    }

    /**
     * @return the cycleIndex
     */
    public int getCycleIndex() {
        return cycleIndex;
    }

    /**
     * @param cycleIndex the cycleIndex to set
     */
    public void setCycleIndex(int cycleIndex) {
        this.cycleIndex = cycleIndex;
        lastPriceMillis = 0;
    }

    @Override
    public void orderCancel() {
        if (inAPIOrder) {
            ordersController.cancelOrder(orderCIDs.get(pairAssetSymbol));
        }
    }

    @Override
    public void doBuy() {
        // nothing
    }

    @Override
    public void doSell() {
        // nothing
    }
}
