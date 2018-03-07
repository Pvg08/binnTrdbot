/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.domain.market.TickerStatistics;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import org.json.*;


/**
 *
 * @author EVG_Adminer
 */
public class CoinRatingController extends Thread {

    class coinRatingLog {
        String symbol;
        String fullname;
        tradePairProcess pair = null;
        
        int rank = 0;
        float market_cap = 0;
        int events_count = 0;
        
        Float percent_hour = 0f;
        Float percent_day = 0f;
        Float percent_from_begin = 0f;
        Float percent_last = 0f;
        Float percent_enter = 0f;
        
        Float value_begin = 0f;
        Float value_last = 0f;
        Float value_enter = 0f;
        
        Float current_price = 0f;
        Float hour_ago_price = 0f;
        Float day_ago_price = 0f;
        Float hour_ago_volume = 0f;
        Float day_ago_volume = 0f;
        Float volatility = 0f;
        int up_counter = 0;
        
        
        boolean do_remove_flag = false;
        boolean fastbuy_skip = false;
        
        Float sort = 0f;
        int update_counter = 0;
        
        String last_event_date;
    }
    
    enum CoinRatingSort {
        CR_RANK,
        CR_MARKET_CAP,
        CR_LAST_HOUR_PRICEUP,
        CR_PROGSTART_PRICEUP,
        CR_24HR_PRICEUP,
        CR_EVENTS_COUNT,
        CR_LAST_EVENT_DATE,
        CR_VOLATILITY,
        CR_CALCULATED_RATING
    }
    
    private mainApplication app;
    BinanceApiRestClient client = null;
    private boolean lowHold = true;
    private boolean autoOrder = true;
    private boolean analyzer = false;
    private CoinRatingSort sortby = CoinRatingSort.CR_RANK;
    private boolean sortAsc = true;

    Map<String, coinRatingLog> heroesMap = new HashMap<String, coinRatingLog>();
    Map<String, JSONObject> coinRanks = new HashMap<String, JSONObject>();

    private long delayTime = 20;
    private static DecimalFormat df3p = new DecimalFormat("0.##%");
    private static DecimalFormat df3 = new DecimalFormat("0.##");
    private static DecimalFormat df6 = new DecimalFormat("0.#####");
    private boolean need_stop = false;
    private boolean paused = false;
    private DefaultListModel<String> listHeroesModel = new DefaultListModel<String>();
    private List<String> accountCoins = new ArrayList<>(0);

    private coinRatingLog entered = null;

    public CoinRatingController(mainApplication application) {
        app = application;
    }

    private void checkFromTop() {
        int min_counter = -1;
        String min_key = "";
        for (Entry<String, coinRatingLog> entry : heroesMap.entrySet()) {
            coinRatingLog curr = entry.getValue();
            if (min_counter < 0 || min_counter > curr.update_counter) {
                min_counter = curr.update_counter;
                min_key = entry.getKey();
            }
        }
        if (!min_key.isEmpty()) {
            if (heroesMap.get(min_key).update_counter == 0) {
                checkPair(heroesMap.get(min_key));
            } else {
                updatePair(heroesMap.get(min_key));
            }
        }
    }
    
    private void updatePair(coinRatingLog curr) {
        curr.update_counter++;
        List<Candlestick> bars_h = client.getCandlestickBars(curr.symbol, CandlestickInterval.FIVE_MINUTES, 13, System.currentTimeMillis() - 60*60*1000, System.currentTimeMillis() + 10000);
        List<Candlestick> bars_d = client.getCandlestickBars(curr.symbol, CandlestickInterval.TWO_HOURLY, 13, System.currentTimeMillis() - 24*60*60*1000, System.currentTimeMillis() + 100000);
        if (bars_h.size() > 1) {
            curr.hour_ago_price = Float.parseFloat(bars_h.get(0).getClose());
            curr.volatility = 0f;
            curr.hour_ago_volume = 0f;
            for(int i=0; i<bars_h.size(); i++) {
                float min_price = Float.parseFloat(bars_h.get(i).getLow());
                float max_price = Float.parseFloat(bars_h.get(i).getHigh());
                float open_price = Float.parseFloat(bars_h.get(i).getOpen());
                float close_price = Float.parseFloat(bars_h.get(i).getClose());
                curr.volatility += Math.abs((open_price - min_price)/open_price);
                curr.volatility += Math.abs((close_price - min_price)/close_price);
                curr.volatility += Math.abs((max_price - open_price)/open_price);
                curr.volatility += Math.abs((max_price - close_price)/close_price);
                curr.hour_ago_volume += Float.parseFloat(bars_h.get(i).getVolume());
            }
            curr.volatility /= bars_h.size()*4;
        }
        if (bars_d.size() > 1) {
            curr.day_ago_price = Float.parseFloat(bars_d.get(0).getClose());
            curr.day_ago_volume = 0f;
            for(int i=0; i<bars_d.size(); i++) {
                curr.day_ago_volume += Float.parseFloat(bars_d.get(i).getVolume());
            }
        }
    }
    
    private void checkPair(coinRatingLog curr) {
        curr.update_counter++;
        
        app.log("-----------------------------------");
        app.log("Analyzing pair " + curr.symbol + ":");
        ExchangeInfo info = client.getExchangeInfo();
        SymbolInfo pair_sinfo = info.getSymbolInfo(curr.symbol);
        
        int dips_50p = 0;
        int dips_25p = 0;
        int dips_10p = 0;
        
        int peaks_50p = 0;
        int peaks_25p = 0;
        int peaks_10p = 0;
        
        int h_bars = 0;
        String last_hbar_info = "";
        
        updatePair(curr);
        
        List<Candlestick> bars = client.getCandlestickBars(curr.symbol, CandlestickInterval.TWO_HOURLY);
        
        app.log("Base = " + pair_sinfo.getBaseAsset());
        app.log("Base name = " + curr.fullname);
        app.log("Base rank = " + curr.rank);
        app.log("Base marketcap = " + df3.format(curr.market_cap) + " USD");
        app.log("Quote = " + pair_sinfo.getQuoteAsset());
        app.log("Current volatility = " + df6.format(curr.volatility));
        
        if (bars.size() > 0) {
            app.log("Price = " + bars.get(bars.size()-1).getClose());
            String timeframe = df3.format(2.0*bars.size()/24) + "d";
            app.log("");
            app.log("Timeframe = " + timeframe);
        }

        for(int i=0; i<bars.size(); i++) {
            Candlestick bar = bars.get(i);
            float open = Float.parseFloat(bar.getOpen());
            float close = Float.parseFloat(bar.getClose());
            float mid = 0.5f * (open + close);
            float min = Float.parseFloat(bar.getLow());
            float max = Float.parseFloat(bar.getHigh());
            
            float dip_p = 100 * (open - min) / open;
            float peak_p = 100 * (max - open) / open;
            float change_p = 100 * (close - open) / close;
            
            if (dip_p >= 10) {dips_10p++;}
            if (dip_p >= 25) {dips_25p++;}
            if (dip_p >= 50) {dips_50p++;}
            
            if (peak_p >= 10) {peaks_10p++;}
            if (peak_p >= 25) {peaks_25p++;}
            if (peak_p >= 50) {peaks_50p++;}
            
            if (dip_p > 25 && change_p > -5) {
                h_bars++;
                last_hbar_info = DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm").format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(bar.getOpenTime()), ZoneId.systemDefault()));
                last_hbar_info += " (DIP "+df3.format(dip_p)+"; CHANGE "+df3.format(change_p)+")";
            }
        }
        
        if (peaks_10p > 0) {
            app.log("Peaks 10% = " + peaks_10p);
        }
        if (peaks_25p > 0) {
            app.log("Peaks 25% = " + peaks_25p);
        }
        if (peaks_50p > 0) {
            app.log("Peaks 50% = " + peaks_50p);
        }
        if (dips_10p > 0) {
            app.log("Dips 10% = " + dips_10p);
        }
        if (dips_25p > 0) {
            app.log("Dips 25% = " + dips_25p);
        }
        if (dips_50p > 0) {
            app.log("Dips 50% = " + dips_50p);
        }
        if (h_bars > 0) {
            app.log("H BARS = " + h_bars + " last = " + last_hbar_info);
        }
        
        try {
            JSONObject obj = JsonReader.readJsonFromUrl("https://coindar.org/api/v1/coinEvents?name=" + pair_sinfo.getBaseAsset().toLowerCase());
            JSONArray events = obj.getJSONArray("DATA");
            app.log("");
            app.log("Events count: " + events.length());
            curr.events_count = events.length();
            if (events.length() > 0) {
                app.log("Last event: " + events.getJSONObject(0).optString("caption_ru", "-"));
                app.log("Last event date: " + events.getJSONObject(0).optString("start_date", "-"));
                curr.last_event_date = events.getJSONObject(0).optString("start_date", "");
            }
        } catch (Exception ex) {
            Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        app.log("-----------------------------------");
    }
    
    private void loadRatingData() {
        try {
            JSONObject obj = JsonReader.readJsonFromUrl("https://api.coinmarketcap.com/v1/ticker/?limit=1500");
            JSONArray coins = obj.getJSONArray("DATA");
            for (int i = 0; i < coins.length(); i++) {
                String symbol = coins.getJSONObject(i).getString("symbol");
                String symbol_name = coins.getJSONObject(i).getString("name").toUpperCase();
                coinRanks.put(symbol, coins.getJSONObject(i));
                if (!coinRanks.containsKey(symbol_name)) {
                    coinRanks.put(symbol_name, coins.getJSONObject(i));
                }            
            }
        } catch (Exception ex) {
            Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
        }
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

    private static Map<String, coinRatingLog> sortByComparator(Map<String, coinRatingLog> unsortMap, final boolean order) {
        List<Entry<String, coinRatingLog>> list = new LinkedList<Entry<String, coinRatingLog>>(unsortMap.entrySet());
        // Sorting the list based on values
        Collections.sort(list, new Comparator<Entry<String, coinRatingLog>>() {
            public int compare(Entry<String, coinRatingLog> o1,
                    Entry<String, coinRatingLog> o2) {
                if (order) {
                    return o1.getValue().sort.compareTo(o2.getValue().sort);
                } else {
                    return o2.getValue().sort.compareTo(o1.getValue().sort);
                }
            }
        });
        // Maintaining insertion order with the help of LinkedList
        Map<String, coinRatingLog> sortedMap = new LinkedHashMap<String, coinRatingLog>();
        for (Entry<String, coinRatingLog> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    private void updateList() {
        
        for (Entry<String, coinRatingLog> entry : heroesMap.entrySet()) {
            if (sortby == CoinRatingSort.CR_RANK) {
                entry.getValue().sort = (float) entry.getValue().rank;
            } else if (sortby == CoinRatingSort.CR_MARKET_CAP) {
                entry.getValue().sort = (float) entry.getValue().market_cap;
            } else if (sortby == CoinRatingSort.CR_PROGSTART_PRICEUP) {
                entry.getValue().sort = (float) entry.getValue().percent_from_begin;
            } else if (sortby == CoinRatingSort.CR_24HR_PRICEUP) {
                entry.getValue().sort = (float) entry.getValue().percent_day;
            } else if (sortby == CoinRatingSort.CR_LAST_HOUR_PRICEUP) {
                entry.getValue().sort = (float) entry.getValue().percent_hour;
            } else if (sortby == CoinRatingSort.CR_EVENTS_COUNT) {
                entry.getValue().sort = (float) entry.getValue().events_count;
            } else if (sortby == CoinRatingSort.CR_CALCULATED_RATING) {
                entry.getValue().sort = (float) entry.getValue().market_cap; // @todo
            } else if (sortby == CoinRatingSort.CR_VOLATILITY) {
                entry.getValue().sort = (float) entry.getValue().volatility;
            } else if (sortby == CoinRatingSort.CR_LAST_EVENT_DATE) {
                entry.getValue().sort = (float) entry.getValue().market_cap; // @todo
            }
        }
        
        Map<String, coinRatingLog> sortedMapAsc = sortByComparator(heroesMap, sortAsc);
        int index = 0;
        for (Entry<String, coinRatingLog> entry : sortedMapAsc.entrySet()) {
            coinRatingLog curr = entry.getValue();
            if (curr != null) {
                String text;
                text = (curr.rank > 0 && curr.rank < 9999) ? "(" + curr.rank + ") " : "";
                text += curr.symbol + ": " + df3p.format(curr.percent_from_begin) + " * " + df3p.format(curr.percent_last);
                if (curr.up_counter > 1) {
                    text += " [" + curr.up_counter + "]";
                }
                if (curr.value_enter > 0) {
                    text += " [H]";
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
            for (Entry<String, coinRatingLog> entry : heroesMap.entrySet()) {
                coinRatingLog curr = entry.getValue();
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
                    entered.value_enter = entered.current_price;
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

        app.log("Coin rating thread starting...");

        if (analyzer) {
            loadRatingData();
        }
        
        need_stop = false;
        paused = false;

        doWait(1000);

        Account account = app.getProfitsChecker().getAccount();
        List<AssetBalance> allBalances = account.getBalances();
        allBalances.forEach((balance) -> {
            if (Float.parseFloat(balance.getFree()) > 0 /*&& !balance.getAsset().equals("BNB") && !balance.getAsset().equals("BTC")*/) {
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
                            coinRatingLog clog = heroesMap.get(symbol);
                            clog.value_last = clog.current_price;
                            clog.current_price = rprice;
                            clog.do_remove_flag = false;
                            if (clog.value_last > 0) {
                                clog.percent_last = (clog.current_price - clog.value_last) / clog.value_last;
                            }
                            if (clog.value_begin > 0) {
                                clog.percent_from_begin = (clog.current_price - clog.value_begin) / clog.value_begin;
                            }
                            if (clog.hour_ago_price > 0) {
                                clog.percent_hour = (clog.current_price - clog.hour_ago_price) / clog.hour_ago_price;
                            }
                            if (clog.day_ago_price > 0) {
                                clog.percent_day = (clog.current_price - clog.day_ago_price) / clog.day_ago_price;
                            }
                            if (clog.percent_last > 0.0005) {
                                clog.up_counter++;
                            } else if (clog.percent_last < -1) {
                                clog.up_counter = 0;
                            } else if (clog.percent_last < -0.001) {
                                clog.up_counter = clog.up_counter / 2 - 1;
                                if (clog.up_counter < 0) {
                                    clog.up_counter = 0;
                                }
                            } else if (clog.percent_last < -0.0005) {
                                clog.up_counter = clog.up_counter - 1;
                                if (clog.up_counter < 0) {
                                    clog.up_counter = 0;
                                }
                            }
                            if (clog.value_enter > 0) {
                                clog.percent_enter = (clog.current_price - clog.value_enter) / clog.value_enter;
                            }
                        } else {
                            coinRatingLog newlog = new coinRatingLog();
                            newlog.symbol = symbol;
                            newlog.do_remove_flag = false;
                            newlog.current_price = rprice;
                            newlog.value_last = rprice;
                            newlog.value_begin = rprice;
                            newlog.percent_last = 0f;
                            newlog.percent_from_begin = 0f;
                            newlog.percent_enter = 0f;
                            newlog.up_counter = 0;
                            newlog.update_counter = 0;
                            newlog.value_enter = 0f;
                            newlog.pair = null;
                            newlog.fastbuy_skip = false;
                            
                            String psymbol = symbol.substring(0, symbol.length() - 3).toUpperCase();
                            if (coinRanks.containsKey(psymbol)) {
                                newlog.rank = Integer.parseInt(coinRanks.get(psymbol).optString("rank", "9999"));
                                newlog.market_cap = Float.parseFloat(coinRanks.get(psymbol).optString("market_cap_usd", "0"));
                                newlog.fullname = coinRanks.get(psymbol).optString("name", symbol);
                            } else {
                                newlog.rank = 9999;
                                newlog.market_cap = 0;
                                newlog.fullname = symbol;
                            }
                            
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
            
            if (analyzer) {
                checkFromTop();
            }

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

    /**
     * @return the analyzer
     */
    public boolean isAnalyzer() {
        return analyzer;
    }

    /**
     * @param analyzer the analyzer to set
     */
    public void setAnalyzer(boolean analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * @return the sortby
     */
    public CoinRatingSort getSortby() {
        return sortby;
    }

    /**
     * @param sortby the sortby to set
     */
    public void setSortby(CoinRatingSort sortby) {
        this.sortby = sortby;
        updateList();
    }

    /**
     * @return the sortAsc
     */
    public boolean isSortAsc() {
        return sortAsc;
    }

    /**
     * @param sortAsc the sortAsc to set
     */
    public void setSortAsc(boolean sortAsc) {
        this.sortAsc = sortAsc;
        updateList();
    }
}
