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
import com.evgcompany.binntrdbot.CoinFilters;
import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.mainApplication;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.KShortestPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 *
 * @author EVG_adm_T
 */
public class CoinInfoAggregator extends PeriodicProcessThread {
    
    private static final CoinInfoAggregator instance = new CoinInfoAggregator();
    
    private DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph = null;
    private DefaultDirectedGraph<String, DefaultEdge> graph_pair = null;
    private AccountCostUpdateEvent accountCostUpdate = null;
    
    private final Semaphore SEMAPHORE_UPDATE = new Semaphore(1, true);
    
    private Set<String> quoteAssets = null;
    private Set<String> baseAssets = null;
    private Set<String> coins = null;
    private Set<String> coinsToCheck = null;
    private final Map<String, String[]> coinPairs = new HashMap<>();
    private final Map<String, CoinFilters> pairFilters = new HashMap<>();
    private final Map<String, Double> lastPrices = new HashMap<>();
    private final Map<String, Double> tempPrices = new HashMap<>();
    
    private TradingAPIAbstractInterface client = null;
    
    private String baseCoin = "BTC";
    private double baseCoinMinCount = 0.001;
    private double baseAccountCost = 0;
    private double initialAccountCost = -1;
    
    private boolean init_complete = false;
    private long last_init_millis = 0;
    private long reinit_millis = 24 * 60 * 60 * 1000;
    
    private long pricesUpdateTimeMillis = 0;
    private long coinsUpdateTimeMillis = 0;
    
    private CoinInfoAggregator() {
        delayTime = 30;
    }
    
    public static CoinInfoAggregator getInstance(){
        return instance;
    }
    
    public void StartAndWaitForInit() {
        need_stop = false;
        boolean starting = false;
        if (!isAlive()) {
            starting = true;
            start();
            while (!isAlive()) {
                doWait(1000);
            }
            doWait(100);
        }
        waitForInit();
        if (starting) {
            doWait(500);
        }
    }
    
    public void waitForInit() {
        try {
            SEMAPHORE_UPDATE.acquire();
        } catch(InterruptedException exx) {}
        SEMAPHORE_UPDATE.release();
    }
    
    private void addPairToGraph(String symbol1, String symbol2, double price) {
        if (!graph.containsVertex(symbol1)) graph.addVertex(symbol1);
        if (!graph.containsVertex(symbol2)) graph.addVertex(symbol2);

        if (!graph_pair.containsVertex(symbol1)) graph_pair.addVertex(symbol1);
        if (!graph_pair.containsVertex(symbol2)) graph_pair.addVertex(symbol2);
        if (!graph_pair.containsEdge(symbol1, symbol2)) graph_pair.addEdge(symbol1, symbol2);
        
        if (!graph.containsEdge(symbol1, symbol2)) graph.addEdge(symbol1, symbol2);
        DefaultWeightedEdge edge1 = graph.getEdge(symbol1, symbol2);
        graph.setEdgeWeight(edge1, price > 0 ? price : 0);

        if (!graph.containsEdge(symbol2, symbol1)) graph.addEdge(symbol2, symbol1);
        DefaultWeightedEdge edge2 = graph.getEdge(symbol2, symbol1);
        graph.setEdgeWeight(edge2, price > 0 ? 1/price : 0);
    }
    
    private void setPairPriceToGraph(String symbol1, String symbol2, double price) {
        addPairToGraph(symbol1, symbol2, price);
    }
    
    private void addPairToGraph(String pair, double price) {
        String[] coins = coinPairs.get(pair);
        if (coins != null) {
            if (quoteAssets.contains(coins[0]) || quoteAssets.contains(coins[1])) {
                addPairToGraph(coins[0], coins[1], price);
            }
        }
    }
    
    private void initGraph() {
        graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph_pair = new DefaultDirectedGraph<>(DefaultEdge.class);
        coinPairs.forEach((pkey, pcoins)->{
            if (pcoins.length == 2) {
                addPairToGraph(pcoins[0], pcoins[1], lastPrices.get(pkey));
            }
        });
        mainApplication.getInstance().log("Coin graph built: Vertexes=" + graph.vertexSet().size() + "; Edges=" + graph.edgeSet().size() + "; Straight pairs=" + graph_pair.edgeSet().size());
        mainApplication.getInstance().log("Quote assets: " + quoteAssets);
    }
    
    private double getPathWeight(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph, List<String> cycle) {
        double totalWeight = 1;
        for(int i = 1; i < cycle.size(); i++){
            double weight = graph.getEdgeWeight(graph.getEdge(cycle.get(i-1), cycle.get(i)));
            totalWeight *= weight;
        }
        return totalWeight;
    }
    
    public double convertSumm(String symbol1, double price, String symbol2) {
        if (symbol1.equals(symbol2)) {
            return price;
        } else if (lastPrices.containsKey(symbol1+symbol2)) {
            return price * lastPrices.get(symbol1+symbol2);
        } else if (lastPrices.containsKey(symbol2+symbol1)) {
            return price / lastPrices.get(symbol2+symbol1);
        } else if (tempPrices.containsKey(symbol1+symbol2)) {
            return price * tempPrices.get(symbol1+symbol2);
        } else if (tempPrices.containsKey(symbol2+symbol1)) {
            return price / tempPrices.get(symbol2+symbol1);
        }
        KShortestPaths pathsF = new KShortestPaths(graph, 4);
        List<GraphPath<String, DefaultWeightedEdge>> paths = pathsF.getPaths(symbol1, symbol2);
        if (paths.isEmpty()) return 0;
        double weight = getPathWeight(graph, paths.get(0).getVertexList());
        tempPrices.put(symbol1+symbol2, weight);
        return price * weight;
    }
    
    public double convertSumm(String symbol1, double price) {
        return convertSumm(symbol1, price, baseCoin);
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
        if (initialAccountCost < 0) {
            initialAccountCost = baseAccountCost;
        }
        if (accountCostUpdate != null) {
            accountCostUpdate.onUpdate(this);
        }
    }
    
    private void updatePrices(boolean needSync) {
        try {
            if (needSync) SEMAPHORE_UPDATE.acquire();
            List<TickerPrice> allPrices = client.getAllPrices();
            allPrices.forEach((price) -> {
                lastPrices.put(price.getSymbol(), Double.parseDouble(price.getPrice()));
                addPairToGraph(price.getSymbol().toUpperCase(), Double.valueOf(price.getPrice()));
            });
            tempPrices.clear();
            pricesUpdateTimeMillis = System.currentTimeMillis();
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinInfoAggregator.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (needSync) SEMAPHORE_UPDATE.release();
    }
    public void updatePrices() {
        updatePrices(false);
    }
    
    private void doInitCoinsToCheck() {
        Set<String> tmpCoinsToCheck = new HashSet<>();
        List<AssetBalance> allBalances = client.getAllBalances();
        allBalances.forEach((balance) -> {
            if (
                (Float.parseFloat(balance.getFree()) + Float.parseFloat(balance.getLocked())) > 0.0
            ) {
                tmpCoinsToCheck.add(balance.getAsset());
            }
        });
        coinsToCheck = new HashSet<>();
        tmpCoinsToCheck.forEach((checkCoin) -> {
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
    
    private void doMainInit(boolean initial) {
        
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
                            pairFilters.put(info.getBaseAsset() + info.getQuoteAsset(), new CoinFilters(info.getBaseAsset(), info.getQuoteAsset(), info.getFilters()));
                            lastPrices.put(info.getBaseAsset() + info.getQuoteAsset(), Double.parseDouble(price.getPrice()));
                            quoteAssets.add(info.getQuoteAsset());
                            baseAssets.add(info.getBaseAsset());
                            coins.add(info.getBaseAsset());
                            coins.add(info.getQuoteAsset());
                        }
                    }
                });
            }
            tempPrices.clear();
            pricesUpdateTimeMillis = System.currentTimeMillis();
            coinsUpdateTimeMillis = System.currentTimeMillis();
            
            initGraph();
            
            if (initial) {
                doInitCoinsToCheck();
                updatePrices();
            }
            
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinInfoAggregator.class.getName()).log(Level.SEVERE, null, ex);
        }
        SEMAPHORE_UPDATE.release();
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
        mainApplication.getInstance().log("Starting coins update thread...");
        if (!init_complete) {
            mainApplication.getInstance().log("Getting coins & pairs info...");
            doMainInit(true);
            mainApplication.getInstance().log("Enough amount coins: " + coinsToCheck);
            updateBaseAccountCost();
            init_complete = true;
        }
    }

    @Override
    protected void runBody() {
        if ((System.currentTimeMillis() - last_init_millis) > reinit_millis) {
            init_complete = false;
            doMainInit(false);
            init_complete = true;
        } else {
            updatePrices(true);
        }
        updateBaseAccountCost();
    }

    @Override
    protected void runFinish() {
        mainApplication.getInstance().log("Stopping coins update thread...");
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

    /**
     * @param client the client to set
     */
    public void setClient(TradingAPIAbstractInterface client) {
        this.client = client;
    }

    /**
     * @param baseCoin the baseCoin to set
     */
    public void setBaseCoin(String baseCoin) {
        this.baseCoin = baseCoin.toUpperCase().trim();
        baseAccountCost = 0;
        initialAccountCost = -1;
    }

    /**
     * @param baseCoinMinCount the baseCoinMinCount to set
     */
    public void setBaseCoinMinCount(double baseCoinMinCount) {
        this.baseCoinMinCount = baseCoinMinCount;
    }

    /**
     * @return the reinit_millis
     */
    public long getReinitMillis() {
        return reinit_millis;
    }

    /**
     * @param reinit_millis the reinit_millis to set
     */
    public void setReinitMillis(long reinit_millis) {
        this.reinit_millis = reinit_millis;
    }

    /**
     * @return the graph
     */
    public DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> getPricesGraph() {
        return graph;
    }

    /**
     * @return the graph_pair
     */
    public DefaultDirectedGraph<String, DefaultEdge> getPairsGraph() {
        return graph_pair;
    }

    /**
     * @return the pricesUpdateTimeMillis
     */
    public long getPricesUpdateTimeMillis() {
        return pricesUpdateTimeMillis;
    }

    /**
     * @return the coinsUpdateTimeMillis
     */
    public long getCoinsUpdateTimeMillis() {
        return coinsUpdateTimeMillis;
    }

    /**
     * @return the pairFilters
     */
    public Map<String, CoinFilters> getPairFilters() {
        return pairFilters;
    }

    /**
     * @return the accountCostUpdate
     */
    public AccountCostUpdateEvent getAccountCostUpdate() {
        return accountCostUpdate;
    }

    /**
     * @param accountCostUpdate the accountCostUpdate to set
     */
    public void setAccountCostUpdate(AccountCostUpdateEvent accountCostUpdate) {
        this.accountCostUpdate = accountCostUpdate;
    }

    /**
     * @return the initialAccountCost
     */
    public double getInitialAccountCost() {
        return initialAccountCost;
    }
}
