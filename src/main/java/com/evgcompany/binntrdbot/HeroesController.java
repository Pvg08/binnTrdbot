/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.market.TickerPrice;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.DefaultListModel;

class currencyLog {
    String symbol;
    Float percent_from_begin;
    Float percent_last;
    Float percent_enter;
    Float value_begin;
    Float value_last;
    Float value_enter;
    Float value;
    int up_counter = 0;
    boolean do_remove_flag = false;
    tradePairProcess pair = null;
    boolean fastbuy_skip = false;
}

class HeroesCurrencyComparator implements Comparator<currencyLog> {

    @Override
    public int compare(currencyLog o1, currencyLog o2) {
        return o1.percent_from_begin.compareTo(o2.percent_from_begin);
    }
}

/**
 *
 * @author EVG_Adminer
 */
public class HeroesController extends Thread {

    private mainApplication app;
    BinanceApiRestClient client = null;
    private boolean lowHold = true;
    private boolean autoOrder = true;

    Map<String, currencyLog> heroesMap = new HashMap<String, currencyLog>();

    private long delayTime = 20;
    private static DecimalFormat df3 = new DecimalFormat("0.##%");
    private boolean need_stop = false;
    private boolean paused = false;
    private DefaultListModel<String> listHeroesModel = new DefaultListModel<String>();
    private List<String> accountCoins = new ArrayList<>(0);
    
    private currencyLog entered = null;
    
    public HeroesController(mainApplication application) {
        app = application;
    }

    public void doStop() {
        need_stop = true;
        paused = false;
    }

    void doSetPaused(boolean _paused) {
        paused = _paused;
    }

    private void doWait(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private static Map<String, currencyLog> sortByComparator(Map<String, currencyLog> unsortMap, final boolean order) {

        List<Entry<String, currencyLog>> list = new LinkedList<Entry<String, currencyLog>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Entry<String, currencyLog>>() {
            public int compare(Entry<String, currencyLog> o1,
                    Entry<String, currencyLog> o2) {
                if (order) {
                    return o1.getValue().percent_from_begin.compareTo(o2.getValue().percent_from_begin);
                } else {
                    return o2.getValue().percent_from_begin.compareTo(o1.getValue().percent_from_begin);

                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<String, currencyLog> sortedMap = new LinkedHashMap<String, currencyLog>();
        for (Entry<String, currencyLog> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    private void updateList() {
        Map<String, currencyLog> sortedMapAsc = sortByComparator(heroesMap, false);
        int index = 0;
        for (Entry<String, currencyLog> entry : sortedMapAsc.entrySet()) {
            currencyLog curr = entry.getValue();
            if (curr != null) {
                String text = curr.symbol + ": " + df3.format(curr.percent_from_begin) + " * " + df3.format(curr.percent_last);
                if (curr.up_counter > 1) {
                    text = text + " [" + curr.up_counter + "]";
                }
                if (curr.value_enter > 0) {
                    text = text + " [H]";
                }
                if (index < listHeroesModel.size()) {
                    listHeroesModel.set(index, text);
                } else {
                    listHeroesModel.addElement(text);
                }
                index++;
            }
        }
        for (; index < listHeroesModel.size(); index++) {
            listHeroesModel.remove(index);
        }
    }

    private void checkFastEnter() {
        if (entered == null && !heroesMap.isEmpty()) {
            String pairMax = "";
            float maxK = 0;
            for (Entry<String, currencyLog> entry : heroesMap.entrySet()) {
                currencyLog curr = entry.getValue();
                if (curr != null && !curr.fastbuy_skip && curr.up_counter >= 5 && curr.percent_from_begin > 0.03 && curr.percent_last > 0.002) {
                    float k = curr.percent_from_begin * curr.up_counter;
                    if (k > maxK) {
                        pairMax = entry.getKey();
                    }
                }
            }
            if (!pairMax.isEmpty()) {
                entered = heroesMap.get(pairMax);
                if (entered != null) {
                    app.log("Trying to enter with " + pairMax);
                    tradePairProcess nproc = new tradePairProcess(app, client, app.getProfitsChecker(), pairMax);
                    nproc.setTryingToSellUp(false);
                    nproc.setSellUpAll(false);
                    nproc.setTryingToBuyDip(false);
                    nproc.set_do_remove_flag(false);
                    nproc.setTradingBalancePercent(100);
                    nproc.setMainStrategy("No");
                    nproc.setBarInterval("1m");
                    nproc.setDelayTime(5);
                    nproc.setBuyOnStart(true);
                    app.getPairs().add(nproc);
                    nproc.start();
                    entered.pair = nproc;
                    entered.value_enter = entered.value;
                    entered.percent_enter = 0f;
                }
            }
        } else if (entered != null) {
            if (!entered.pair.isHodling() || (entered.percent_last < 0 && entered.up_counter == 0 && (!lowHold || entered.percent_enter > 0))) {
                if (entered.pair.isHodling()) {
                    entered.pair.doSell();
                    doWait(1000);
                } else {
                    entered.fastbuy_skip = true;
                }
                app.getPairs().remove(entered.pair);
                app.getProfitsChecker().removeCurrencyPair(entered.pair.getSymbol());
                entered.pair.doStop();
                entered.pair = null;
                entered.value_enter = 0f;
                entered.percent_enter = 0f;
                entered = null;
            }
        }
    }
    
    @Override
    public void run() {

        app.log("Heroes thread starting...");
        
        need_stop = false;
        paused = false;

        doWait(1000);
        
        Account account = app.getProfitsChecker().getAccount();
        List<AssetBalance> allBalances = account.getBalances();        
        allBalances.forEach((balance)->{
            if (Float.parseFloat(balance.getFree()) > 0 && !balance.getAsset().equals("BNB") && !balance.getAsset().equals("BTC")) {
                accountCoins.add(balance.getAsset());
            }
        });
        String coinsPattern = String.join("|", accountCoins);
        
        while (!need_stop) {
            if (paused) {
                doWait(12000);
                continue;
            }

            heroesMap.forEach((symbol, curr) -> {
                curr.do_remove_flag = true;
            });
            List<TickerPrice> allPrices = client.getAllPrices();
            if (heroesMap.isEmpty()) {
                app.log("Found " + allPrices.size() + " prices...");
                app.log("Checking them using pattern: " + coinsPattern);
            }

            allPrices.forEach((price) -> {
                if (price != null && !price.getSymbol().isEmpty() && !price.getSymbol().contains("1") && !price.getPrice().isEmpty()) {
                    String symbol = price.getSymbol();
                    if (symbol.matches(".*(" + coinsPattern + ")$")) {
                        float rprice = Float.parseFloat(price.getPrice());
                        if (heroesMap.containsKey(symbol)) {
                            currencyLog clog = heroesMap.get(symbol);
                            clog.value_last = clog.value;
                            clog.value = rprice;
                            clog.do_remove_flag = false;
                            if (clog.value_last > 0) {
                                clog.percent_last = (clog.value - clog.value_last) / clog.value_last;
                            }
                            if (clog.value_begin > 0) {
                                clog.percent_from_begin = (clog.value - clog.value_begin) / clog.value_begin;
                            }
                            if (clog.percent_last > 0.0005) {
                                clog.up_counter++;
                            } else if (clog.percent_last < -1) {
                                clog.up_counter = 0;
                            } else if (clog.percent_last < -0.001) {
                                clog.up_counter = clog.up_counter / 2  - 1;
                                if (clog.up_counter < 0) {
                                    clog.up_counter = 0;
                                }
                            } else if (clog.percent_last < -0.0005) {
                                clog.up_counter = clog.up_counter  - 1;
                                if (clog.up_counter < 0) {
                                    clog.up_counter = 0;
                                }
                            }
                            if (clog.value_enter > 0) {
                                clog.percent_enter = (clog.value - clog.value_enter) / clog.value_enter;
                            }
                        } else {
                            currencyLog newlog = new currencyLog();
                            newlog.symbol = symbol;
                            newlog.do_remove_flag = false;
                            newlog.value = rprice;
                            newlog.value_last = rprice;
                            newlog.value_begin = rprice;
                            newlog.percent_last = 0f;
                            newlog.percent_from_begin = 0f;
                            newlog.percent_enter = 0f;
                            newlog.up_counter = 0;
                            newlog.value_enter = 0f;
                            newlog.pair = null;
                            newlog.fastbuy_skip = false;
                            heroesMap.put(symbol, newlog);
                        }
                    }
                }
            });
            heroesMap.forEach((symbol, curr) -> {
                if (curr.do_remove_flag) {
                    heroesMap.remove(symbol);
                }
            });
            updateList();
            
            if (autoOrder) {
                checkFastEnter();
            }

            doWait(delayTime * 1000);
        }
    }

    /**
     * @return the delayTime
     */
    public long getDelayTime() {
        return delayTime;
    }

    /**
     * @param delayTime the delayTime to set
     */
    public void setDelayTime(long delayTime) {
        this.delayTime = delayTime;
    }

    /**
     * @return the listHeroesModel
     */
    public DefaultListModel<String> getListHeroesModel() {
        return listHeroesModel;
    }

    void setClient(BinanceApiRestClient _client) {
        client = _client;
    }

    /**
     * @return the lowHold
     */
    public boolean isLowHold() {
        return lowHold;
    }

    /**
     * @param lowHold the lowHold to set
     */
    public void setLowHold(boolean lowHold) {
        this.lowHold = lowHold;
    }

    /**
     * @return the autoOrder
     */
    public boolean isAutoOrder() {
        return autoOrder;
    }

    /**
     * @param autoOrder the autoOrder to set
     */
    public void setAutoOrder(boolean autoOrder) {
        this.autoOrder = autoOrder;
    }
}
