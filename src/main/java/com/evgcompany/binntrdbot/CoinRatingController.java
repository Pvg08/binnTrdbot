/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerPrice;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JProgressBar;
import org.json.*;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

/**
 *
 * @author EVG_Adminer
 */
public class CoinRatingController extends Thread {

    enum CoinRatingSort {
        CR_RANK,
        CR_MARKET_CAP,
        CR_PROGSTART_PRICEUP,
        CR_LAST_HOUR_PRICEUP,
        CR_24HR_PRICEUP,
        CR_EVENTS_COUNT,
        CR_LAST_EVENT_ANNO_DATE,
        CR_VOLATILITY,
        CR_STRATEGIES_SHOULD_ENTER,
        CR_STRATEGIES_SHOULD_EXIT,
        CR_STRATEGIES_DIRECTION,
        CR_CALCULATED_RATING
    }
    
    private final mainApplication app;
    private tradePairProcessController paircontroller;
    private SignalController signalcontroller = null;
    private TradingAPIAbstractInterface client = null;
    private Closeable orderEvent = null;
    
    private StrategiesController strategiesController = null;
    private boolean lowHold = true;
    private boolean autoOrder = true;
    private boolean autoFastOrder = false;
    private boolean analyzer = false;
    private CoinRatingSort sortby = CoinRatingSort.CR_RANK;
    private boolean sortAsc = true;

    Map<String, CoinRatingPairLogItem> heroesMap = new HashMap<>();
    Map<String, JSONObject> coinRanks = new HashMap<>();

    private long delayTime = 2;
    private long updateTime = 10;
    private boolean have_all_coins_info = false;
    
    private static final DecimalFormat df3p = new DecimalFormat("0.##%");
    private static final DecimalFormat df3 = new DecimalFormat("0.##");
    private static final DecimalFormat df6 = new DecimalFormat("0.#####");
    private boolean need_stop = false;
    private boolean paused = false;
    private final DefaultListModel<String> listHeroesModel = new DefaultListModel<>();
    private final List<String> accountCoins = new ArrayList<>(0);

    private CoinRatingPairLogItem entered = null;
    private long enterMillis = 0;
    private JProgressBar progressBar = null;

    public CoinRatingController(mainApplication application, tradePairProcessController paircontroller) {
        app = application;
        this.paircontroller = paircontroller;
        strategiesController = new StrategiesController();
        strategiesController.setMainStrategy("No strategy");
        signalcontroller = new SignalController();
    }

    private void checkFromTop() {
        int non_zero_count = 0;
        int min_counter = -1;
        String min_key = "";
        for (Entry<String, CoinRatingPairLogItem> entry : heroesMap.entrySet()) {
            CoinRatingPairLogItem curr = entry.getValue();
            if (min_counter < 0 || min_counter > curr.update_counter) {
                min_counter = curr.update_counter;
                min_key = entry.getKey();
            }
            if (curr.update_counter > 0) {
                non_zero_count++;
            }
        }
        if (!min_key.isEmpty()) {
            if (heroesMap.get(min_key).update_counter == 0) {
                checkPair(heroesMap.get(min_key));
            } else {
                updatePair(heroesMap.get(min_key));
            }
        }
        if (progressBar != null) {
            progressBar.setMaximum(heroesMap.size());
            progressBar.setValue(non_zero_count);
        }
        if (min_counter > 0) {
            have_all_coins_info = true;
        }
    }
    
    private void updatePair(CoinRatingPairLogItem curr) {
        curr.update_counter++;
        List<Bar> bars_h = client.getBars(curr.symbol, "5m");
        List<Bar> bars_d = client.getBars(curr.symbol, "2h", 13, System.currentTimeMillis() - 24*60*60*1000, System.currentTimeMillis() + 100000);
        float volatility_hour = 0, volatility_day = 0;
        if (bars_h.size() > 1) {
            int hour_ago_index = Math.max(0, bars_h.size()-12);
            int last_index = bars_h.size()-1;
            curr.current_price = bars_h.get(last_index).getClosePrice().floatValue();
            curr.hour_ago_price = bars_h.get(hour_ago_index).getClosePrice().floatValue();
            if (curr.hour_ago_price > 0) {
                curr.percent_hour = (curr.current_price - curr.hour_ago_price) / curr.hour_ago_price;
            }
            curr.hour_volume = 0f;
            for(int i=hour_ago_index; i<=last_index; i++) {
                curr.hour_volume += bars_h.get(i).getVolume().floatValue();
            }
            BaseTimeSeries series = new BaseTimeSeries(curr.symbol + "_SERIES");
            client.addSeriesBars(series, bars_h);
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            SMAIndicator sma = new SMAIndicator(closePrice, 12);
            StandardDeviationIndicator vol_indicator = new StandardDeviationIndicator(closePrice, 12);
            volatility_hour = vol_indicator.getValue(series.getEndIndex()).floatValue() / sma.getValue(series.getEndIndex()).floatValue();
        }
        if (bars_d.size() > 1) {
            curr.day_ago_price = bars_d.get(0).getClosePrice().floatValue();
            if (curr.day_ago_price > 0) {
                curr.percent_day = (curr.current_price - curr.day_ago_price) / curr.day_ago_price;
            }
            curr.day_volume = 0f;
            for(int i=0; i<bars_d.size(); i++) {
                curr.day_volume += bars_d.get(i).getVolume().floatValue();
            }
            BaseTimeSeries series = new BaseTimeSeries(curr.symbol + "_SERIES");
            client.addSeriesBars(series, bars_d);
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            SMAIndicator sma = new SMAIndicator(closePrice, bars_d.size());
            StandardDeviationIndicator vol_indicator = new StandardDeviationIndicator(closePrice, bars_d.size());
            volatility_day = vol_indicator.getValue(series.getEndIndex()).floatValue() / sma.getValue(series.getEndIndex()).floatValue();
        }
        curr.volatility = volatility_hour * 0.7f + volatility_day * 0.3f;
        strategiesController.setGroupName(curr.symbol);
        strategiesController.setBarSeconds(300);
        strategiesController.resetSeries();
        client.addSeriesBars(strategiesController.getSeries(), bars_h);
        strategiesController.resetStrategies();
        curr.strategies_shouldenter_cnt = strategiesController.getStrategiesEnterActive(6);
        curr.strategies_shouldexit_cnt = strategiesController.getStrategiesExitActive(6);
        curr.calculateRating();
        curr.last_rating_update_millis = System.currentTimeMillis();
    }
    
    private void checkPair(CoinRatingPairLogItem curr) {
        curr.update_counter++;
        
        app.log("-----------------------------------");
        app.log("Analyzing pair " + curr.symbol + ":");
        SymbolInfo pair_sinfo = client.getSymbolInfo(curr.symbol);
        
        int dips_50p = 0;
        int dips_25p = 0;
        int dips_10p = 0;
        
        int peaks_50p = 0;
        int peaks_25p = 0;
        int peaks_10p = 0;
        
        int h_bars = 0;
        String last_hbar_info = "";
        
        updatePair(curr);
        
        List<Bar> bars = client.getBars(curr.symbol, "2h");
        
        app.log("Base = " + pair_sinfo.getBaseAsset());
        app.log("Base name = " + curr.fullname);
        app.log("Base rank = " + curr.rank);
        app.log("Base marketcap = " + df3.format(curr.market_cap) + " USD");
        app.log("Quote = " + pair_sinfo.getQuoteAsset());
        app.log("Current volatility = " + df6.format(curr.volatility));
        
        if (bars.size() > 0) {
            app.log("Price = " + bars.get(bars.size()-1).getClosePrice());
            String timeframe = df3.format(2.0*bars.size()/24) + "d";
            app.log("");
            app.log("Timeframe = " + timeframe);
        }

        for(int i=0; i<bars.size(); i++) {
            Bar bar = bars.get(i);
            float open = bar.getOpenPrice().floatValue();
            float close = bar.getClosePrice().floatValue();
            float min = bar.getMinPrice().floatValue();
            float max = bar.getMaxPrice().floatValue();
            
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
                last_hbar_info = DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm").format(bar.getBeginTime());
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
                String last_anno = events.getJSONObject(0).optString("public_date", "");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-dd HH:mm");
                Date date = sdf.parse(last_anno);
                curr.last_event_anno_millis = date.toInstant().toEpochMilli();
            }
        } catch (IOException | ParseException | JSONException ex) {
            Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        curr.calculateRating();
        
        app.log("Volatility = " + df3p.format(curr.volatility));
        app.log("Hour volume = " + df3.format(curr.hour_volume));
        app.log("Day volume = " + df3.format(curr.day_volume));
        app.log("Hour change percent = " + df3p.format(curr.percent_hour));
        app.log("Day change percent = " + df3p.format(curr.percent_day));
        
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
        } catch (IOException | JSONException ex) {
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

    private static Map<String, CoinRatingPairLogItem> sortByComparator(Map<String, CoinRatingPairLogItem> unsortMap, final boolean order) {
        List<Entry<String, CoinRatingPairLogItem>> list = new LinkedList<>(unsortMap.entrySet());
        // Sorting the list based on values
        Collections.sort(list, new Comparator<Entry<String, CoinRatingPairLogItem>>() {
            @Override
            public int compare(Entry<String, CoinRatingPairLogItem> o1,
                    Entry<String, CoinRatingPairLogItem> o2) {
                if (order) {
                    return o1.getValue().sort.compareTo(o2.getValue().sort);
                } else {
                    return o2.getValue().sort.compareTo(o1.getValue().sort);
                }
            }
        });
        // Maintaining insertion order with the help of LinkedList
        Map<String, CoinRatingPairLogItem> sortedMap = new LinkedHashMap<>();
        for (Entry<String, CoinRatingPairLogItem> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    private void updateList() {
        
        for (Entry<String, CoinRatingPairLogItem> entry : heroesMap.entrySet()) {
            if (null != sortby) switch (sortby) {
                case CR_RANK:
                    entry.getValue().sort = (float) entry.getValue().rank;
                    break;
                case CR_MARKET_CAP:
                    entry.getValue().sort = (float) entry.getValue().market_cap;
                    break;
                case CR_PROGSTART_PRICEUP:
                    entry.getValue().sort = (float) entry.getValue().percent_from_begin;
                    break;
                case CR_24HR_PRICEUP:
                    entry.getValue().sort = (float) entry.getValue().percent_day;
                    break;
                case CR_LAST_HOUR_PRICEUP:
                    entry.getValue().sort = (float) entry.getValue().percent_hour;
                    break;
                case CR_EVENTS_COUNT:
                    entry.getValue().sort = (float) entry.getValue().events_count;
                    break;
                case CR_CALCULATED_RATING:
                    entry.getValue().sort = (float) entry.getValue().rating;
                    break;
                case CR_VOLATILITY:
                    entry.getValue().sort = (float) entry.getValue().volatility;
                    break;
                case CR_STRATEGIES_SHOULD_ENTER:
                    entry.getValue().sort = (float) entry.getValue().strategies_shouldenter_cnt;
                    break;
                case CR_STRATEGIES_SHOULD_EXIT:
                    entry.getValue().sort = (float) entry.getValue().strategies_shouldexit_cnt;
                    break;
                case CR_STRATEGIES_DIRECTION:
                    entry.getValue().sort = (float) entry.getValue().strategies_shouldenter_cnt - entry.getValue().strategies_shouldexit_cnt;
                    break;
                case CR_LAST_EVENT_ANNO_DATE:
                    entry.getValue().sort = (float) entry.getValue().last_event_anno_millis;
                    break;
                default:
                    break;
            }
        }
        
        Map<String, CoinRatingPairLogItem> sortedMapAsc = sortByComparator(heroesMap, sortAsc);
        int index = 0;
        for (Entry<String, CoinRatingPairLogItem> entry : sortedMapAsc.entrySet()) {
            CoinRatingPairLogItem curr = entry.getValue();
            if (curr != null) {
                String text;
                text = (curr.rank > 0 && curr.rank < 9999) ? "(" + curr.rank + ") " : "";
                text += curr.symbol + ": ";
                
                if (null != sortby) switch (sortby) {
                    case CR_MARKET_CAP:
                        text += df3.format(curr.market_cap);
                        break;
                    case CR_PROGSTART_PRICEUP:
                        text += df3p.format(curr.percent_from_begin);
                        break;
                    case CR_24HR_PRICEUP:
                        text += df3p.format(curr.percent_day);
                        break;
                    case CR_LAST_HOUR_PRICEUP:
                        text += df3p.format(curr.percent_hour);
                        break;
                    case CR_EVENTS_COUNT:
                        text += curr.events_count;
                        break;
                    case CR_CALCULATED_RATING:
                        text += df3.format(curr.rating);
                        break;
                    case CR_VOLATILITY:
                        text += df3p.format(curr.volatility);
                        break;
                    case CR_STRATEGIES_SHOULD_ENTER:
                        text += curr.strategies_shouldenter_cnt;
                        break;
                    case CR_STRATEGIES_SHOULD_EXIT:
                        text += curr.strategies_shouldexit_cnt;
                        break;
                    case CR_STRATEGIES_DIRECTION:
                        text += curr.strategies_shouldenter_cnt - curr.strategies_shouldexit_cnt;
                        break;
                    case CR_LAST_EVENT_ANNO_DATE:
                        text+=curr.last_event_date != null && !curr.last_event_date.isEmpty() ? curr.last_event_date : "Unknown date";
                        break;
                    default:
                        text+=df6.format(curr.current_price);
                        break;
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
        if (entered == null && !heroesMap.isEmpty() && !app.getPairController().getPairs().isEmpty()) {
            String pairMax = "";
            float maxK = 0;
            for (Entry<String, CoinRatingPairLogItem> entry : heroesMap.entrySet()) {
                CoinRatingPairLogItem curr = entry.getValue();
                if (
                        curr != null && 
                        !curr.fastbuy_skip && 
                        curr.last_rating_update_millis > 0 &&
                        curr.last_rating_update_millis > System.currentTimeMillis() - 600000 &&
                        curr.volatility > 0.007 &&
                        curr.percent_hour > 0.002 &&
                        curr.market_cap > 200000 &&
                        curr.hour_volume > curr.day_volume / 24 &&
                        (curr.rank == 9999 || curr.rank < 400) &&
                        curr.strategies_shouldenter_cnt > 1
                ) {
                    float k = curr.rating;
                    if (k > maxK) {
                        pairMax = entry.getKey();
                        maxK = k;
                    }
                }
            }
            if (!pairMax.isEmpty() && !paircontroller.hasPair(pairMax)) {
                enterMillis = System.currentTimeMillis();
                entered = heroesMap.get(pairMax);
                if (entered != null) {
                    if (autoFastOrder) {
                        app.log("Trying to auto fast-enter with pair: " + pairMax);
                        entered.pair = paircontroller.addPairFastRun(pairMax);
                    } else {
                        app.log("Trying to auto enter with pair: " + pairMax);
                        entered.pair = paircontroller.addPair(pairMax);
                    }
                    doWait(1000);
                }
            }
        } else if (entered != null) {
            if (
                    (
                        entered.pair.isInitialized() && 
                        !entered.pair.isTriedBuy() && 
                        (System.currentTimeMillis() - enterMillis) > 10 * 60 * 1000 // max wait time = 10min
                    ) ||
                    (
                        entered.pair.isInitialized() && 
                        entered.pair.isTriedBuy() && 
                        !entered.pair.isHodling() && 
                        !entered.pair.isInLimitOrder()
                    )
            ) {
                paircontroller.removePair(entered.pair.getSymbol());
                if (entered.pair.getLastTradeProfit().compareTo(BigDecimal.ZERO) < 0) {
                    entered.fastbuy_skip = true;
                }
                entered.pair = null;
                entered = null;
            }
        }
    }

    private void updateCurrentPrices(String coinsPattern) {
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
                        CoinRatingPairLogItem clog = heroesMap.get(symbol);
                        clog.current_price = rprice;
                        clog.do_remove_flag = false;
                        if (clog.start_price > 0) {
                            clog.percent_from_begin = (clog.current_price - clog.start_price) / clog.start_price;
                        }
                        if (clog.hour_ago_price > 0) {
                            clog.percent_hour = (clog.current_price - clog.hour_ago_price) / clog.hour_ago_price;
                        }
                        if (clog.day_ago_price > 0) {
                            clog.percent_day = (clog.current_price - clog.day_ago_price) / clog.day_ago_price;
                        }
                    } else {
                        CoinRatingPairLogItem newlog = new CoinRatingPairLogItem();
                        newlog.symbol = symbol;
                        newlog.do_remove_flag = false;
                        newlog.current_price = rprice;
                        newlog.start_price = rprice;
                        newlog.percent_from_begin = 0f;
                        newlog.update_counter = 0;
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
    }
    
    @Override
    public void run() {

        app.log("Coin rating thread starting...");

        if (analyzer) {
            loadRatingData();
        }
        
        need_stop = false;
        paused = false;
        have_all_coins_info = false;

        doWait(1000);

        signalcontroller.startSignalsProcess();
        
        List<AssetBalance> allBalances = client.getAllBalances();
        allBalances.forEach((balance) -> {
            if ((Float.parseFloat(balance.getFree()) + Float.parseFloat(balance.getLocked())) > 0.00001 && !balance.getAsset().equals("BNB")) {
                accountCoins.add(balance.getAsset());
            }
        });
        String coinsPattern = String.join("|", accountCoins);

        orderEvent = client.OnOrderEvent(null, event -> {
            app.log("OrderEvent: " + event.getType().name() + " " + event.getSide().name() + " " + event.getSymbol() + "; Qty=" + event.getAccumulatedQuantity() + "; Price=" + event.getPrice(), true, true);
            app.systemSound();
        });
        
        while (!need_stop) {
            if (paused) {
                doWait(delayTime * 1000);
                continue;
            }

            updateCurrentPrices(coinsPattern);
            
            if (analyzer) {
                checkFromTop();
            }
            updateList();

            if (analyzer && autoOrder && have_all_coins_info) {
                checkFastEnter();
            }

            doWait(have_all_coins_info ? updateTime * 1000 : delayTime * 1000);
        }
        
        signalcontroller.stopSignalsProcess();
        
        if (orderEvent != null) {
            try {
                orderEvent.close();
            } catch (IOException ex) {
                Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
            }
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

    void setClient(TradingAPIAbstractInterface _client) {
        client = _client;
        signalcontroller.setClient(client);
    }

    /**
     * @return the signalcontroller
     */
    public SignalController getSignalController() {
        return signalcontroller;
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

    /**
     * @return the updateTime
     */
    public long getUpdateTime() {
        return updateTime;
    }

    /**
     * @param updateTime the updateTime to set
     */
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * @param progressBar the progressBar to set
     */
    public void setProgressBar(JProgressBar progressBar) {
        this.progressBar = progressBar;
    }

    /**
     * @return the autoFastOrder
     */
    public boolean isAutoFastOrder() {
        return autoFastOrder;
    }

    /**
     * @param autoFastOrder the autoFastOrder to set
     */
    public void setAutoFastOrder(boolean autoFastOrder) {
        this.autoFastOrder = autoFastOrder;
    }
}
