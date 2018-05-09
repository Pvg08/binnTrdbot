/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.evgcompany.binntrdbot.analysis.CoinCycleController;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import java.math.BigDecimal;
import java.text.DecimalFormat;
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
public class tradeCycleProcess extends PeriodicProcessThread {
    
    TradingAPIAbstractInterface client = null;
    CoinCycleController cycleController = null;

    private int cycleIndex = 0; 
    
    private String symbol;
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    private String mainAsset;

    private boolean isBuy;
    private CoinFilters filter = null;
    private CoinInfoAggregator info = null;

    private final List<String> cycle = new ArrayList<>();
    private final List<BigDecimal> prices = new ArrayList<>();
    private final Map<String, BigDecimal> cycleCoins = new HashMap<>();
    private final Set<String> usedPairs = new HashSet<>();
    private int cycleStep;

    private int switchesCount = 0;
    private long limitOrderId = 0;
    private tradeProfitsController profitsChecker = null;
    private long startMillis = 0;
    private long lastOrderMillis = 0;
    private long lastPriceMillis = 0;
    
    private BigDecimal current_order_amount;
    private BigDecimal current_order_price;
    
    private BigDecimal initialQty = null;
    private String initialAsset = null;
    private double profitAim;
    
    private boolean reverting = false;
    
    private static final DecimalFormat df8 = new DecimalFormat("0.########");

    public tradeCycleProcess(CoinCycleController cycleController) {
        this.cycleController = cycleController;
        client = cycleController.getClient();
        profitsChecker = cycleController.getPairProcessController().getProfitsChecker();
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
            profitsChecker.removeCurrencyPair(pair);
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
            String[] coins = info.getCoinPairs().get(cpair);
            profitsChecker.placeOrUpdatePair(coins[0], coins[1], cpair, true);
            profitsChecker.setPairPrice(cpair, prices.get(i));
        }
        profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        updateOrderPairPrices();
    }

    private void doInitWaitNextStep() {
        lastPriceMillis = 0;
        limitOrderId = 0;
        profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
    }
    
    private void doPostStep() {
        for(int i = 0; i<cycle.size(); i++) {
            profitsChecker.removeCurrencyPair(getChainPair(cycle.get(i)));
        }
    }
    
    private void doSwitchStep() {
        if (switchesCount >= cycleController.getMaxSwitchesCount()) {
            return;
        }
        if (cycleController.doCycleSwap(this)) {
            mainApplication.getInstance().log("We wait too long. Need to switch "+symbol+"'s cycle order...");
            if (limitOrderId > 0) {
                if (!profitsChecker.isTestMode()) {
                    Order order = client.getOrderStatus(symbol, limitOrderId);
                    if (order.getStatus() == OrderStatus.FILLED || 
                        order.getStatus() == OrderStatus.PARTIALLY_FILLED || 
                        order.getStatus() == OrderStatus.CANCELED ||
                        order.getStatus() == OrderStatus.EXPIRED ||
                        order.getStatus() == OrderStatus.REJECTED
                    ) {
                        return;
                    }
                    profitsChecker.cancelOrder(symbol, limitOrderId);
                }
                doWait(1000);
            }
            limitOrderId = 0;
            profitsChecker.finishOrder(symbol, false, null);
            switchesCount++;
            doPreStep(cycleStep);
            initAndOrderStep();
        }
    }
    
    private void initAndOrderStep() {
        updateOrderPairPrices();
        lastOrderMillis = System.currentTimeMillis();
        symbol = cycle.get(cycleStep);
        isBuy = chainIsBuy(symbol);
        symbol = getChainPair(symbol);
        filter = info.getPairFilters().get(symbol);
        baseAssetSymbol = filter.getBaseAssetSymbol();
        quoteAssetSymbol = filter.getQuoteAssetSymbol();
        if (mainAsset.isEmpty()) {
            mainAsset = isBuy ? quoteAssetSymbol : baseAssetSymbol;
        }
        symbol = baseAssetSymbol+quoteAssetSymbol;
        
        profitsChecker.placeOrUpdatePair(baseAssetSymbol, quoteAssetSymbol, symbol, true);
        
        current_order_price = prices.get(cycleStep);
        profitsChecker.setPairPrice(symbol, current_order_price);
        if (cycleStep == 0) {
            if (isBuy) {
                current_order_amount = profitsChecker.getOrderAssetAmount(quoteAssetSymbol, cycleController.getTradingBalancePercent());
                cycleCoins.put(quoteAssetSymbol, current_order_amount.stripTrailingZeros());
            } else {
                current_order_amount = profitsChecker.getOrderAssetAmount(baseAssetSymbol, cycleController.getTradingBalancePercent());
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
            profitsChecker.PreBuy(symbol, current_order_amount, current_order_price);
        }
        lastPriceMillis = 0;
        doOrder(baseAssetSymbol, quoteAssetSymbol, isBuy, false, current_order_price, current_order_amount);
    }
    
    private void doNextStep() {
        if (need_stop) {
            return;
        }
        if (limitOrderId != 0) {
            return;
        }
        if (cycleStep >= 0 && isBuy) {
            profitsChecker.finishOrder(symbol, true, current_order_price);
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
        if (profitsChecker.isTestMode()) {
            doWait(10000);
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
    
    private void orderSuccessfullyFinish() {
        lastPriceMillis = 0;
        limitOrderId = 0;
        profitsChecker.finishOrder(symbol, true, null);
        if (!profitsChecker.isTestMode()) {
            profitsChecker.updateAllBalances(true);
        } else {
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
    }
    private void orderAbort() {
        if (limitOrderId > 0) {
            if (!profitsChecker.isTestMode()) {
                profitsChecker.cancelOrder(symbol, limitOrderId);
            }
            doWait(1000);
        }
        profitsChecker.finishOrder(symbol, false, null);
        limitOrderId = -1;
        lastPriceMillis = 0;
        
        revertCoinCycle();
        
        if (!profitsChecker.isTestMode()) {
            profitsChecker.updateAllBalances(true);
        }
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
        
        profitsChecker.finishOrder(symbol, true, null);
        if (!profitsChecker.isTestMode()) {
            profitsChecker.updateAllBalances(true);
        } else {
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
    }
    
    private void doRevertOrder(BigDecimal amount, List<String> path) {
        mainApplication.getInstance().log("Reverting " + amount + " " + path, true, true);
        if (path.isEmpty()) return;
        if (path.size() > 1) {
            // @todo
        }
        if (cycleStep >= 0 && isBuy) {
            profitsChecker.finishOrder(symbol, true, null);
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
        symbol = path.get(0);
        isBuy = chainIsBuy(symbol);
        symbol = getChainPair(symbol);
        filter = new CoinFilters(symbol, client);
        filter.logFiltersInfo();
        profitsChecker.placeOrUpdatePair(filter.getBaseAssetSymbol(), filter.getQuoteAssetSymbol(), symbol, true);
        baseAssetSymbol = filter.getBaseAssetSymbol();
        quoteAssetSymbol = filter.getQuoteAssetSymbol();
        current_order_amount = amount;
        if (info.getLastPrices().containsKey(symbol)) {
            current_order_price = BigDecimal.valueOf(info.getLastPrices().get(symbol));
        }
        if (!isBuy) {
            profitsChecker.PreBuy(symbol, current_order_amount, current_order_price);
        }
        doOrder(baseAssetSymbol, quoteAssetSymbol, isBuy, true, current_order_price, current_order_amount);
        doWait(2000);
    }
    
    private void setOrderCoins(Order order) {
        String orderCoins[] = info.getCoinPairs().get(order.getSymbol());
        BigDecimal executed = new BigDecimal(order.getExecutedQty());
        BigDecimal original = new BigDecimal(order.getOrigQty());
        BigDecimal price = new BigDecimal(order.getPrice());
        if (order.getSide() == OrderSide.SELL) {
            cycleCoins.put(orderCoins[0], original.subtract(executed).stripTrailingZeros());
            cycleCoins.put(orderCoins[1], executed.multiply(price).stripTrailingZeros());
        } else {
            cycleCoins.put(orderCoins[0], executed.stripTrailingZeros());
            cycleCoins.put(orderCoins[1], original.subtract(executed).multiply(price).stripTrailingZeros());
        }
    }
    
    private void checkOrders() {
        if (need_stop) {
            return;
        }
        if (limitOrderId <= 0) {
            return;
        }
        Order order = client.getOrderStatus(symbol, limitOrderId);

        System.out.println(order);
        
        if (order == null) {
            return;
        }
        
        setOrderCoins(order);
        
        if (order.getStatus() == OrderStatus.PARTIALLY_FILLED && 
            Double.parseDouble(order.getExecutedQty()) < Double.parseDouble(order.getOrigQty())
        ) {
            return;
        }

        if (
            order.getStatus() == OrderStatus.FILLED || 
            order.getStatus() == OrderStatus.PARTIALLY_FILLED || 
            order.getStatus() == OrderStatus.CANCELED ||
            order.getStatus() == OrderStatus.EXPIRED ||
            order.getStatus() == OrderStatus.REJECTED
        ) {
            if (order.getStatus() == OrderStatus.FILLED && Double.parseDouble(order.getExecutedQty()) >= Double.parseDouble(order.getOrigQty())) {
                orderSuccessfullyFinish();
            } else {
                limitOrderId = 0;
                if (!reverting) orderAbort();
            }
        } else if (cycleStep >= 1 && Double.parseDouble(order.getExecutedQty()) <= 0 && cycleController.getAbortMarketApproxProfit(initialQty, new BigDecimal(order.getOrigQty()), initialAsset, baseAssetSymbol, quoteAssetSymbol) > profitAim) {
            mainApplication.getInstance().log("We'll have more profit if abort this "+symbol+" cycle order...");
            if (!reverting) orderAbort();
        } else {
            if (cycleController.isUseStopLimited() && (
                    (cycleStep >= 1 && (System.currentTimeMillis()-lastOrderMillis) > cycleController.getStopLimitTimeout() * 1000) ||
                    (cycleStep == 0 && Double.parseDouble(order.getExecutedQty()) <= 0 && (System.currentTimeMillis()-lastOrderMillis) > cycleController.getStopFirstLimitTimeout() * 1000)
                )
            ) {
                mainApplication.getInstance().log("We wait too long. Need to stop this "+symbol+"'s cycle order...");
                if (!reverting) orderAbort();
            } else if (cycleStep >= 1 && cycleStep < (cycle.size() - 1) && Double.parseDouble(order.getExecutedQty()) <= 0 && (System.currentTimeMillis()-lastOrderMillis) > cycleController.getSwitchLimitTimeout() * 1000) {
                if (!reverting) doSwitchStep();
            }
        }
    }
    
    private void doOrder(String baseSymbol, String quoteSymbol, boolean isBuying, boolean isMarket, BigDecimal price, BigDecimal amount) {
        String symbol_order = baseSymbol+quoteSymbol;
        if (isBuying) {
            filter.setCurrentPrice(price);
            filter.setCurrentAmount(amount);
            filter.prepareForBuy(profitsChecker);
            BigDecimal tobuy_price = filter.getCurrentPrice();
            BigDecimal tobuy_amount = filter.getCurrentAmount();
            if (profitsChecker.canBuy(symbol_order, tobuy_amount, tobuy_price)) {
                
                if (initialQty == null && initialAsset == null) {
                    initialAsset = quoteSymbol;
                    initialQty = tobuy_amount.multiply(tobuy_price);
                }
                
                mainApplication.getInstance().log("BYING " + df8.format(tobuy_amount) + " " + baseSymbol + " for " + df8.format(tobuy_amount.multiply(tobuy_price)) + " " + quoteSymbol + " (price=" + df8.format(tobuy_price) + ")", true, true);
                long result = profitsChecker.Buy(symbol_order, tobuy_amount, isMarket ? null : tobuy_price);
                if (result >= 0) {
                    limitOrderId = result;
                    mainApplication.getInstance().log("Successful!", true, true);
                    lastOrderMillis = System.currentTimeMillis();
                    if (limitOrderId == 0) {
                        cycleCoins.put(baseSymbol, tobuy_amount.stripTrailingZeros());
                        cycleCoins.put(quoteSymbol, amount.compareTo(tobuy_amount) > 0 ? amount.subtract(tobuy_amount).multiply(tobuy_price).stripTrailingZeros() : BigDecimal.ZERO);
                        if (!reverting) doInitWaitNextStep();
                    }
                } else {
                    mainApplication.getInstance().log("Error!", true, true);
                    if (!reverting) orderAbort();
                }
            } else {
                BigDecimal avail = profitsChecker.getAvailableCount(quoteSymbol);
                if (avail == null) avail = BigDecimal.valueOf(-1);
                mainApplication.getInstance().log("Can't buy " + df8.format(tobuy_amount) + " " + baseSymbol + " for " + df8.format(tobuy_amount.multiply(tobuy_price)) + " " + quoteSymbol + " (price=" + df8.format(tobuy_price) + ")" + " (avail summ = "+avail+")");
                if (!reverting) orderAbort();
            }
        } else {
            filter.setCurrentPrice(price);
            filter.setCurrentAmount(amount);
            filter.prepareForSell(profitsChecker);
            BigDecimal tosell_price = filter.getCurrentPrice();
            BigDecimal tosell_amount = filter.getCurrentAmount();
            if (profitsChecker.canSell(symbol_order, tosell_amount)) {
                
                if (initialQty == null && initialAsset == null) {
                    initialAsset = baseSymbol;
                    initialQty = tosell_amount;
                }
                
                mainApplication.getInstance().log("SELLING " + df8.format(tosell_amount) + " " + baseSymbol + " for " + df8.format(tosell_amount.multiply(tosell_price)) + " " + quoteSymbol + " (price=" + df8.format(tosell_price) + ")", true, true);
                long result = profitsChecker.Sell(symbol_order, tosell_amount, isMarket ? null : tosell_price, null);
                if (result >= 0) {
                    limitOrderId = result;
                    mainApplication.getInstance().log("Successful!", true, true);
                    lastOrderMillis = System.currentTimeMillis();
                    if (limitOrderId == 0) {
                        cycleCoins.put(baseSymbol, amount.compareTo(tosell_amount) > 0 ? amount.subtract(tosell_amount).stripTrailingZeros() : BigDecimal.ZERO);
                        cycleCoins.put(quoteSymbol, tosell_amount.multiply(tosell_price).stripTrailingZeros());
                        if (!profitsChecker.isTestMode()) {
                            profitsChecker.updateAllBalances(true);
                        }
                        if (!reverting) doInitWaitNextStep();
                    }
                } else {
                    mainApplication.getInstance().log("Error!", true, true);
                    if (!reverting) orderAbort();
                }
            } else {
                BigDecimal avail = profitsChecker.getAvailableCount(baseSymbol);
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
            currencyPairItem item = profitsChecker.getPair(cpair);
            if (item != null) {
                item.setPrice(null);
                item.setLastOrderPrice(null);
                item.setMarker("CYCLE" + cycleIndex + " PAIR" + (i+1) + " CLOSED");
                profitsChecker.updatePairText(cpair, false);
            }
        }
        for(int i = from; i < cycle.size(); i++) {
            String cpair = getChainPair(cycle.get(i));
            currencyPairItem item = profitsChecker.getPair(cpair);
            if (item != null) {
                item.setPrice(BigDecimal.valueOf(info.getLastPrices().get(cpair)));
                item.setLastOrderPrice(prices.get(i));
                item.setMarker("CYCLE" + cycleIndex + " PAIR" + (i+1) + (cycleStep == i ? " ACTIVE" : " WAIT"));
                profitsChecker.updatePairText(cpair, false);
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
        limitOrderId = 0;
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
        return limitOrderId>0;
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
}
