/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.coinrating;

import com.evgcompany.binntrdbot.*;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.cycles.CoinCycleController;
import com.evgcompany.binntrdbot.events.GlobalTrendUpdateEvent;
import com.evgcompany.binntrdbot.misc.JsonReader;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import com.evgcompany.binntrdbot.signal.SignalOrderController;
import com.evgcompany.binntrdbot.strategies.core.StrategiesController;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
public class CoinRatingController extends PeriodicProcessThread {

    private final TradePairProcessList paircontroller;
    private TradingAPIAbstractInterface client = null;
    
    private final String serialize_filename = "coinsRating_serialized.bin";
    
    private CoinInfoAggregator coinInfo = null;
    private StrategiesController strategiesController = null;
    private SignalOrderController signalOrderController = null;
    private CoinCycleController coinCycleController = null;
    private boolean lowHold = true;
    private boolean autoOrder = true;
    private boolean autoFastOrder = false;
    private boolean useSignals = false;
    private boolean useCycles = false;

    private boolean analyzer = false;
    private CoinRatingSort sortby = CoinRatingSort.CR_RANK;
    private boolean sortAsc = true;

    private Map<String, CoinRatingPairLogItem> coinPairRatingMap = null;
    private Map<String, CoinRatingLogItem> coinRatingMap = null;
    private final Map<String, JSONObject> coinJSONRanks = new HashMap<>();
    private GlobalTrendUpdateEvent trendUpdateEvent = null;

    private long updateTime = 10;
    private long updateTrendTime = 450;
    private long updateTrendMillis = 0;
    private long updateRanksTime = 7200;
    private long updateRanksMillis = 0;
    private long updateCoinsMillis = 0;
    private long updateLoadRejectTime = 86400;
    private boolean have_all_coins_pairs_info = false;

    private final DefaultListModel<String> coinRatingModel = new DefaultListModel<>();

    private final Map<String, CoinRatingPairLogItem> entered = new HashMap<>();
    private int maxEnter = 3;
    private int secondsOrderEnterWait = 600;
    private float minRatingForOrder = 5;
    private final int globalTrendMaxRank = 40;
    
    private JProgressBar progressBar = null;
    private final double initialCost = -1;
    
    private double upTrendPercent = 0;
    private double downTrendPercent = 0;
    private boolean noAutoBuysOnDowntrend = false;

    public CoinRatingController(mainApplication application, TradePairProcessList paircontroller) {
        this.paircontroller = paircontroller;
        strategiesController = new StrategiesController();
        strategiesController.setMainStrategy("No strategy");
        coinInfo = CoinInfoAggregator.getInstance();
        coinInfo.setClient(client);
        signalOrderController = new SignalOrderController(this, paircontroller);
        signalOrderController.setDelayTime(2);
        coinCycleController = new CoinCycleController(client, this);
        coinCycleController.setDelayTime(90);
    }

    public void saveToFile(String fname) {
        ObjectOutputStream out = null;
        try {
            FileOutputStream bout = new FileOutputStream(fname);
            out = new ObjectOutputStream(bout);
            Long LcurMillis = System.currentTimeMillis();
            Long LupdateRanksMillis = updateRanksMillis;
            Long LupdateCoinsMillis = updateCoinsMillis;
            out.writeObject(LcurMillis);
            out.writeObject(LupdateRanksMillis);
            out.writeObject(LupdateCoinsMillis);
            out.writeObject(coinPairRatingMap);
            out.writeObject(coinRatingMap);
            out.flush();
        } catch (IOException ex) {
            Logger.getLogger(CoinInfoAggregator.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException ex) {
                Logger.getLogger(CoinInfoAggregator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public boolean loadFromFile(String fname) {
        try {
            FileInputStream bin = new FileInputStream(fname);
            ObjectInputStream in = new ObjectInputStream(bin);
            Long LcurMillis = (Long) in.readObject();
            if ((System.currentTimeMillis() - LcurMillis) > updateLoadRejectTime * 1000) {
                return false;
            }
            Long LupdateRanksMillis = (Long) in.readObject();
            Long LupdateCoinsMillis = (Long) in.readObject();
            coinPairRatingMap = (Map<String, CoinRatingPairLogItem>) in.readObject();
            coinRatingMap = (Map<String, CoinRatingLogItem>) in.readObject();
            if (
                LupdateRanksMillis != null && 
                LupdateCoinsMillis != null && 
                coinPairRatingMap != null &&
                coinRatingMap != null
            ) {
                return true;
            }
        } catch(IOException | ClassNotFoundException e) {}
        updateRanksMillis = 0;
        updateCoinsMillis = 0;
        coinPairRatingMap = null;
        coinRatingMap = null;
        return false;
    }
    
    public void updateGlobalTrendData() {
        
        updateTrendMillis = System.currentTimeMillis();
        
        List<String> rankPairs = new ArrayList<>(0);
        double upval = 0;
        double dnval = 0;
        double upcnt = 0;
        double dncnt = 0;
        for (Entry<String, CoinRatingPairLogItem> entry : coinPairRatingMap.entrySet()) {
            if (entry.getValue().base_rating.rank <= globalTrendMaxRank) {
                rankPairs.add(entry.getKey());
            }
        }
        for (String pair : rankPairs) {
            List<Bar> bars_h = client.getBars(pair, "1h", 13, null, null);
            if (bars_h != null && bars_h.size() >= 2) {
                double lastPrice = bars_h.get(bars_h.size()-1).getClosePrice().doubleValue();
                double preLastPrice = bars_h.get(bars_h.size()-2).getClosePrice().doubleValue();
                double percent = 100 * (lastPrice - preLastPrice) / lastPrice;
                if (percent > 0.1) {
                    upval+=0.45*Math.pow(Math.abs(percent), 0.25);
                    upcnt+=0.45;
                } else if (percent < -0.1) {
                    dnval+=0.45*Math.pow(Math.abs(percent), 0.25);
                    dncnt+=0.45;
                }
            }
            if (bars_h != null && bars_h.size() >= 3) {
                double lastPrice = bars_h.get(bars_h.size()-1).getClosePrice().doubleValue();
                double preLastPrice = bars_h.get(bars_h.size()-3).getClosePrice().doubleValue();
                double percent = 100 * (lastPrice - preLastPrice) / lastPrice;
                if (percent > 0.1) {
                    upval+=0.25*Math.pow(Math.abs(percent), 0.25);
                    upcnt+=0.25;
                } else if (percent < -0.1) {
                    dnval+=0.25*Math.pow(Math.abs(percent), 0.25);
                    dncnt+=0.25;
                }
            }
            if (bars_h != null && bars_h.size() >= 5) {
                double lastPrice = bars_h.get(bars_h.size()-1).getClosePrice().doubleValue();
                double preLastPrice = bars_h.get(bars_h.size()-5).getClosePrice().doubleValue();
                double percent = 100 * (lastPrice - preLastPrice) / lastPrice;
                if (percent > 0.1) {
                    upval+=0.13*Math.pow(Math.abs(percent), 0.25);
                    upcnt+=0.13;
                } else if (percent < -0.1) {
                    dnval+=0.13*Math.pow(Math.abs(percent), 0.25);
                    dncnt+=0.13;
                }
            }
            if (bars_h != null && bars_h.size() >= 9) {
                double lastPrice = bars_h.get(bars_h.size()-1).getClosePrice().doubleValue();
                double preLastPrice = bars_h.get(bars_h.size()-9).getClosePrice().doubleValue();
                double percent = 100 * (lastPrice - preLastPrice) / lastPrice;
                if (percent > 0.1) {
                    upval+=0.1*Math.pow(Math.abs(percent), 0.25);
                    upcnt+=0.1;
                } else if (percent < -0.1) {
                    dnval+=0.1*Math.pow(Math.abs(percent), 0.25);
                    dncnt+=0.1;
                }
            }
            if (bars_h != null && bars_h.size() >= 13) {
                double lastPrice = bars_h.get(bars_h.size()-1).getClosePrice().doubleValue();
                double preLastPrice = bars_h.get(bars_h.size()-13).getClosePrice().doubleValue();
                double percent = 100 * (lastPrice - preLastPrice) / lastPrice;
                if (percent > 0.1) {
                    upval+=0.07*Math.pow(Math.abs(percent), 0.25);
                    upcnt+=0.07;
                } else if (percent < -0.1) {
                    dnval+=0.07*Math.pow(Math.abs(percent), 0.25);
                    dncnt+=0.07;
                }
            }
        }
        upTrendPercent = (upval > 0 && dnval > 0) ? 100 * upval / (upval+dnval) : 0;
        downTrendPercent = (upval > 0 && dnval > 0) ? 100 * dnval / (upval+dnval) : 0;
        if (rankPairs.size() > 0) {
            upTrendPercent *= (upcnt+dncnt) / rankPairs.size();
            downTrendPercent *= (upcnt+dncnt) / rankPairs.size();
        }
        if (trendUpdateEvent != null) {
            trendUpdateEvent.onUpdate(upTrendPercent, downTrendPercent);
        }
    }
    
    public boolean setPairSignalRating(String pair, float rating) {
        if (coinPairRatingMap.containsKey(pair)) {
            coinPairRatingMap.get(pair).signal_rating = rating;
            updateList();
            return true;
        }
        return false;
    }
    
    private void checkFromTop() {
        int non_zero_count_coins = 0;
        int non_zero_count_pairs = 0;
        int min_coins_counter = -1;
        int min_pairs_counter = -1;
        String min_coin_key = "";
        String min_pair_key = "";
        
        for (Entry<String, CoinRatingLogItem> entry : coinRatingMap.entrySet()) {
            CoinRatingLogItem curr = entry.getValue();
            if (min_coins_counter < 0 || min_coins_counter > curr.update_counter) {
                min_coins_counter = curr.update_counter;
                min_coin_key = entry.getKey();
            }
            if (curr.update_counter > 0) {
                non_zero_count_coins++;
            }
        }

        for (Entry<String, CoinRatingPairLogItem> entry : coinPairRatingMap.entrySet()) {
            CoinRatingPairLogItem curr = entry.getValue();
            if (min_pairs_counter < 0 || min_pairs_counter > curr.update_counter) {
                min_pairs_counter = curr.update_counter;
                min_pair_key = entry.getKey();
            }
            if (curr.update_counter > 0) {
                non_zero_count_pairs++;
            }
        }
        
        if (!min_coin_key.isEmpty()) {
            if (coinRatingMap.get(min_coin_key).update_counter == 0) {
                checkCoin(coinRatingMap.get(min_coin_key));
                min_pair_key = "";
            }
        }
        
        if (!min_pair_key.isEmpty()) {
            if (coinPairRatingMap.get(min_pair_key).update_counter == 0) {
                checkPair(coinPairRatingMap.get(min_pair_key));
                min_coin_key = "";
            }
        }

        if (!min_pair_key.isEmpty() && coinPairRatingMap.get(min_pair_key).update_counter > 0) {
            if (!min_coin_key.isEmpty() && coinRatingMap.get(min_coin_key).update_counter > 0 && Math.random() > 0.95) {
                updateCoin(coinRatingMap.get(min_coin_key));
            } else {
                updatePair(coinPairRatingMap.get(min_pair_key));
            }
        }

        if (progressBar != null) {
            progressBar.setMaximum(coinRatingMap.size() + coinPairRatingMap.size());
            progressBar.setValue(non_zero_count_coins + non_zero_count_pairs);
        }
        if (min_coins_counter > 0 && min_pairs_counter > 0) {
            have_all_coins_pairs_info = true;
            delayTime = updateTime;
        }
    }
    
    private void updatePair(CoinRatingPairLogItem curr) {

        if (curr.base_rating==null || curr.base_rating.update_counter <= 0) {
            if (coinRatingMap.containsKey(curr.symbolBase)) {
                curr.base_rating = coinRatingMap.get(curr.symbolBase);
            }
        }
        
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
            curr.hour_volume_base = (float) coinInfo.convertSumm(curr.symbolBase, curr.hour_volume, coinInfo.getBaseCoin());
            BaseTimeSeries series = new BaseTimeSeries(curr.symbol + "_SERIES");
            TradingAPIAbstractInterface.addSeriesBars(series, bars_h);
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
            curr.day_volume_base = (float) coinInfo.convertSumm(curr.symbolBase, curr.day_volume, coinInfo.getBaseCoin());
            BaseTimeSeries series = new BaseTimeSeries(curr.symbol + "_SERIES");
            TradingAPIAbstractInterface.addSeriesBars(series, bars_d);
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            SMAIndicator sma = new SMAIndicator(closePrice, bars_d.size());
            StandardDeviationIndicator vol_indicator = new StandardDeviationIndicator(closePrice, bars_d.size());
            volatility_day = vol_indicator.getValue(series.getEndIndex()).floatValue() / sma.getValue(series.getEndIndex()).floatValue();
        }
        curr.is_new_pair = ((bars_d.size() > 0 || bars_h.size() > 0) && bars_d.size() < 12);
        curr.volatility = volatility_hour * 0.7f + volatility_day * 0.3f;
        strategiesController.setGroupName(curr.symbol);
        strategiesController.resetSeries();
        TradingAPIAbstractInterface.addSeriesBars(strategiesController.getSeries(), bars_h);
        strategiesController.resetStrategies();
        curr.strategies_shouldenter_rate = strategiesController.getStrategiesEnterRate(6);
        curr.strategies_shouldexit_rate = strategiesController.getStrategiesExitRate(6);
        if (signalOrderController != null) {
            curr.signal_rating = (float) signalOrderController.getSignalController().getPairSignalRating(curr.symbol);
        }
        curr.calculateRating();
        curr.last_rating_update_millis = System.currentTimeMillis();
        curr.update_counter++;
        System.out.println("UPDATE " + curr.symbol);
    }
    
    private void updateCoin(CoinRatingLogItem curr) {
        if (coinJSONRanks.containsKey(curr.symbol)) {
            curr.rank = Integer.parseInt(coinJSONRanks.get(curr.symbol).optString("rank", "9999"));
            curr.market_cap = Float.parseFloat(coinJSONRanks.get(curr.symbol).optString("market_cap_usd", "0"));
            curr.fullname = coinJSONRanks.get(curr.symbol).optString("name", curr.symbol);
        } else {
            curr.rank = 9999;
            curr.market_cap = 0;
            curr.fullname = curr.symbol;
        }
        curr.calculateRating();
        curr.update_counter++;
        System.out.println("UPDATE " + curr.symbol);
    }
    
    private void checkCoin(CoinRatingLogItem curr) {
        curr.update_counter++;
        mainApplication.getInstance().log("-----------------------------------");
        mainApplication.getInstance().log("Analyzing coin " + curr.symbol + ":");
        
        try {
            JSONObject obj = JsonReader.readJsonFromUrl("https://coindar.org/api/v1/coinEvents?name=" + curr.symbol.toLowerCase());
            JSONArray events = obj.getJSONArray("DATA");
            mainApplication.getInstance().log("");
            mainApplication.getInstance().log("Events count: " + events.length());
            curr.events_count = events.length();
            if (events.length() > 0) {
                mainApplication.getInstance().log("Last event: " + events.getJSONObject(0).optString("caption_ru", "-"));
                mainApplication.getInstance().log("Last event date: " + events.getJSONObject(0).optString("start_date", "-"));
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

        mainApplication.getInstance().log("Rating = " + NumberFormatter.df3.format(curr.rating));
    }
    
    private void checkPair(CoinRatingPairLogItem curr) {
        curr.update_counter++;
        
        mainApplication.getInstance().log("-----------------------------------");
        mainApplication.getInstance().log("Analyzing pair " + curr.symbol + ":");
        
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
        
        mainApplication.getInstance().log("Base = " + curr.symbolBase);
        mainApplication.getInstance().log("Base name = " + curr.base_rating.fullname);
        mainApplication.getInstance().log("Base rank = " + curr.base_rating.rank);
        mainApplication.getInstance().log("Base marketcap = " + NumberFormatter.df3.format(curr.base_rating.market_cap) + " USD");
        mainApplication.getInstance().log("Quote = " + curr.symbolQuote);
        mainApplication.getInstance().log("Current volatility = " + NumberFormatter.df6.format(curr.volatility));
        
        if (bars.size() > 0) {
            mainApplication.getInstance().log("Price = " + bars.get(bars.size()-1).getClosePrice());
            String timeframe = NumberFormatter.df3.format(2.0*bars.size()/24) + "d";
            mainApplication.getInstance().log("");
            mainApplication.getInstance().log("Timeframe = " + timeframe);
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
                last_hbar_info += " (DIP "+NumberFormatter.df3.format(dip_p)+"; CHANGE "+NumberFormatter.df3.format(change_p)+")";
            }
        }
        
        if (peaks_10p > 0) {
            mainApplication.getInstance().log("Peaks 10% = " + peaks_10p);
        }
        if (peaks_25p > 0) {
            mainApplication.getInstance().log("Peaks 25% = " + peaks_25p);
        }
        if (peaks_50p > 0) {
            mainApplication.getInstance().log("Peaks 50% = " + peaks_50p);
        }
        if (dips_10p > 0) {
            mainApplication.getInstance().log("Dips 10% = " + dips_10p);
        }
        if (dips_25p > 0) {
            mainApplication.getInstance().log("Dips 25% = " + dips_25p);
        }
        if (dips_50p > 0) {
            mainApplication.getInstance().log("Dips 50% = " + dips_50p);
        }
        if (h_bars > 0) {
            mainApplication.getInstance().log("H BARS = " + h_bars + " last = " + last_hbar_info);
        }
        
        curr.calculateRating();
        
        mainApplication.getInstance().log("Volatility = " + NumberFormatter.df3p.format(curr.volatility));
        mainApplication.getInstance().log("Hour volume = " + NumberFormatter.df8.format(curr.hour_volume) + " " + curr.symbolQuote + " (" + NumberFormatter.df8.format(curr.hour_volume_base) + " " + coinInfo.getBaseCoin() + ")");
        mainApplication.getInstance().log("Day volume = " + NumberFormatter.df8.format(curr.day_volume) + " " + curr.symbolQuote + " (" + NumberFormatter.df8.format(curr.day_volume_base) + " " + coinInfo.getBaseCoin() + ")");
        mainApplication.getInstance().log("Hour change percent = " + NumberFormatter.df3p.format(curr.percent_hour));
        mainApplication.getInstance().log("Day change percent = " + NumberFormatter.df3p.format(curr.percent_day));
        mainApplication.getInstance().log("Rating = " + NumberFormatter.df3.format(curr.rating));
        
        mainApplication.getInstance().log("-----------------------------------");
    }
    
    private void loadRatingData() {
        System.out.println("Loading RatingData...");
        updateRanksMillis = System.currentTimeMillis();
        try {
            JSONObject obj = JsonReader.readJsonFromUrl("https://api.coinmarketcap.com/v1/ticker/?limit=1500");
            JSONArray coins = obj.getJSONArray("DATA");
            for (int i = 0; i < coins.length(); i++) {
                String symbol = coins.getJSONObject(i).getString("symbol");
                String symbol_name = coins.getJSONObject(i).getString("name").toUpperCase();
                coinJSONRanks.put(symbol, coins.getJSONObject(i));
                if (!coinJSONRanks.containsKey(symbol_name)) {
                    coinJSONRanks.put(symbol_name, coins.getJSONObject(i));
                }            
            }
        } catch (IOException | JSONException ex) {
            Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Map<String, CoinRatingPairLogItem> sortByComparator(Map<String, CoinRatingPairLogItem> unsortMap) {
        List<Entry<String, CoinRatingPairLogItem>> list = new LinkedList<>(unsortMap.entrySet());
        Collections.sort(list, (Entry<String, CoinRatingPairLogItem> o1, Entry<String, CoinRatingPairLogItem> o2) -> {
            if (sortAsc) {
                return o1.getValue().sort.compareTo(o2.getValue().sort);
            } else {
                return o2.getValue().sort.compareTo(o1.getValue().sort);
            }
        });
        Map<String, CoinRatingPairLogItem> sortedMap = new LinkedHashMap<>();
        list.forEach((entry) -> {
            if (coinInfo.getCoinsToCheck().contains(entry.getValue().symbolQuote)) {
                sortedMap.put(entry.getKey(), entry.getValue());
            }
        });
        return sortedMap;
    }

    private void updateList() {
        if (!isAlive()) return;
        for (Entry<String, CoinRatingPairLogItem> entry : coinPairRatingMap.entrySet()) {
            if (null != sortby) switch (sortby) {
                case CR_RANK:
                    entry.getValue().sort = (float) entry.getValue().base_rating.rank;
                    break;
                case CR_MARKET_CAP:
                    entry.getValue().sort = (float) entry.getValue().base_rating.market_cap;
                    break;
                case CR_VOLUME_HOUR:
                    entry.getValue().sort = (float) entry.getValue().hour_volume_base;
                    break;
                case CR_VOLUME_DAY:
                    entry.getValue().sort = (float) entry.getValue().day_volume_base;
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
                    entry.getValue().sort = (float) entry.getValue().base_rating.events_count;
                    break;
                case CR_SIGNALS_RATING:
                    entry.getValue().sort = (float) entry.getValue().signal_rating;
                    break;
                case CR_CALCULATED_RATING:
                    entry.getValue().sort = (float) entry.getValue().rating;
                    break;
                case CR_VOLATILITY:
                    entry.getValue().sort = (float) entry.getValue().volatility;
                    break;
                case CR_STRATEGIES_SHOULD_ENTER:
                    entry.getValue().sort = (float) entry.getValue().strategies_shouldenter_rate;
                    break;
                case CR_STRATEGIES_SHOULD_EXIT:
                    entry.getValue().sort = (float) entry.getValue().strategies_shouldexit_rate;
                    break;
                case CR_STRATEGIES_DIRECTION:
                    entry.getValue().sort = (float) (entry.getValue().strategies_shouldenter_rate - entry.getValue().strategies_shouldexit_rate);
                    break;
                case CR_LAST_EVENT_ANNO_DATE:
                    entry.getValue().sort = (float) entry.getValue().base_rating.last_event_anno_millis;
                    break;
                default:
                    break;
            }
        }
        
        Map<String, CoinRatingPairLogItem> sortedMapAsc = sortByComparator(coinPairRatingMap);
        int index = 0;
        for (Entry<String, CoinRatingPairLogItem> entry : sortedMapAsc.entrySet()) {
            CoinRatingPairLogItem curr = entry.getValue();
            if (curr != null) {
                String text;
                text = (curr.base_rating.rank > 0 && curr.base_rating.rank < 9999) ? "(" + curr.base_rating.rank + ") " : "";
                text += curr.symbol + ": ";
                
                if (null != sortby) switch (sortby) {
                    case CR_MARKET_CAP:
                        text += NumberFormatter.df3.format(curr.base_rating.market_cap);
                        break;
                    case CR_VOLUME_HOUR:
                        text += NumberFormatter.df8.format(curr.hour_volume_base);
                        break;
                    case CR_VOLUME_DAY:
                        text += NumberFormatter.df8.format(curr.day_volume_base);
                        break;
                    case CR_PROGSTART_PRICEUP:
                        text += NumberFormatter.df3p.format(curr.percent_from_begin);
                        break;
                    case CR_24HR_PRICEUP:
                        text += NumberFormatter.df3p.format(curr.percent_day);
                        break;
                    case CR_LAST_HOUR_PRICEUP:
                        text += NumberFormatter.df3p.format(curr.percent_hour);
                        break;
                    case CR_EVENTS_COUNT:
                        text += curr.base_rating.events_count;
                        break;
                    case CR_SIGNALS_RATING:
                        text += NumberFormatter.df3.format(curr.signal_rating);
                        break;
                    case CR_CALCULATED_RATING:
                        text += NumberFormatter.df3.format(curr.rating);
                        break;
                    case CR_VOLATILITY:
                        text += NumberFormatter.df3p.format(curr.volatility);
                        break;
                    case CR_STRATEGIES_SHOULD_ENTER:
                        text += curr.strategies_shouldenter_rate;
                        break;
                    case CR_STRATEGIES_SHOULD_EXIT:
                        text += curr.strategies_shouldexit_rate;
                        break;
                    case CR_STRATEGIES_DIRECTION:
                        text += curr.strategies_shouldenter_rate - curr.strategies_shouldexit_rate;
                        break;
                    case CR_LAST_EVENT_ANNO_DATE:
                        text += curr.base_rating.last_event_date != null && !curr.base_rating.last_event_date.isEmpty() ? curr.base_rating.last_event_date : "Unknown date";
                        break;
                    default:
                        text += NumberFormatter.df8.format(curr.current_price);
                        break;
                }
                
                if (index < coinRatingModel.size()) {
                    coinRatingModel.set(index, text);
                } else {
                    coinRatingModel.addElement(text);
                }
                index++;
            }
        }
        for (; index < coinRatingModel.size(); index++) {
            coinRatingModel.remove(index);
        }
    }
    
    public int getCoinRank(String coin) {
        if (coinRatingMap.containsKey(coin)) {
            return coinRatingMap.get(coin).rank;
        }
        return 9999;
    }
    public float getCoinRating(String coin) {
        if (coinRatingMap.containsKey(coin)) {
            return coinRatingMap.get(coin).rating;
        }
        return -1;
    }
    public float getPairRating(String pair) {
        if (coinPairRatingMap.containsKey(pair)) {
            return coinPairRatingMap.get(pair).rating;
        }
        return -1;
    }
    public float getPairVolatility(String pair) {
        if (coinPairRatingMap.containsKey(pair)) {
            return coinPairRatingMap.get(pair).volatility;
        }
        return 0;
    }
    public float getPairBaseHourVolume(String pair) {
        if (coinPairRatingMap.containsKey(pair)) {
            return (float) coinPairRatingMap.get(pair).hour_volume_base;
        }
        return 0;
    }
    
    private void baseOrderEnter(String pair) {
        if (
            autoOrder && 
            entered.size() < maxEnter && 
            (
                signalOrderController == null ||
                !signalOrderController.getEntered().containsKey(pair)
            ) && 
            !entered.containsKey(pair) && 
            !coinPairRatingMap.isEmpty()
        ) {
            if (!pair.isEmpty() && !paircontroller.hasPair(pair)) {
                CoinRatingPairLogItem toenter = coinPairRatingMap.get(pair);
                if (toenter != null) {
                    if (autoFastOrder) {
                        mainApplication.getInstance().log("Trying to auto fast-enter with pair: " + pair, true, true);
                        toenter.pair = paircontroller.addPairFastRun(pair);
                    } else {
                        mainApplication.getInstance().log("Trying to auto enter with pair: " + pair, true, true);
                        toenter.pair = paircontroller.addPair(pair);
                    }
                    entered.put(pair, toenter);
                    doWait(1000);
                }
            }
        }
    }

    private void checkOrderExit() {
        if (entered.size() > 0) {
            List<String> listRemove = new ArrayList<>();
            for (Entry<String, CoinRatingPairLogItem> entry : entered.entrySet()) {
                CoinRatingPairLogItem rentered = entry.getValue();
                if (rentered != null && rentered.pair != null && rentered.pair.isInitialized()) {
                    if (    (
                                !rentered.pair.isTriedBuy() && 
                                (System.currentTimeMillis() - rentered.pair.getStartMillis()) > secondsOrderEnterWait * 1000 // max wait time = 10min
                            ) || (
                                rentered.pair.isTriedBuy() && 
                                !rentered.pair.isHodling() && 
                                !rentered.pair.isInAPIOrder()
                            ) || (
                                !rentered.pair.isAlive()
                            )
                    ) {
                        mainApplication.getInstance().log("Exit from order: " + rentered.pair.getSymbol(), true, true);
                        paircontroller.removePair(rentered.pair.getSymbol());
                        if (rentered.pair.getLastTradeProfit().compareTo(BigDecimal.ZERO) < 0 || !rentered.pair.isTriedBuy()) {
                            //rentered.fastbuy_skip = true;
                            rentered.rating_inc -= 1;
                        } else {
                            rentered.rating_inc += 0.5;
                        }
                        rentered.calculateRating();
                        listRemove.add(rentered.pair.getSymbol());
                        rentered.pair = null;
                    }
                }
            }
            listRemove.forEach((entry) -> {
                entered.remove(entry);
            });
        }
    }

    private void checkFastEnter() {
        checkOrderExit();
        if (autoOrder && entered.size() < maxEnter && !coinPairRatingMap.isEmpty()) {
            String pairMax = "";
            float maxR = 0;
            for (Entry<String, CoinRatingPairLogItem> entry : coinPairRatingMap.entrySet()) {
                CoinRatingPairLogItem curr = entry.getValue();
                if (
                        curr != null && 
                        !entered.containsKey(curr.symbol) &&
                        !curr.fastbuy_skip && 
                        curr.rating >= minRatingForOrder &&
                        curr.last_rating_update_millis > 0 &&
                        curr.last_rating_update_millis > (System.currentTimeMillis() - 800000) &&
                        curr.volatility > 0.004 &&
                        curr.percent_hour > 0.001 &&
                        (curr.base_rating.market_cap == 0 || curr.base_rating.market_cap > 100000) &&
                        curr.hour_volume > curr.day_volume / 24 &&
                        (curr.base_rating.rank == 9999 || curr.base_rating.rank < 500) &&
                        curr.strategies_shouldenter_rate > curr.strategies_shouldexit_rate
                ) {
                    if (curr.rating > maxR) {
                        pairMax = entry.getKey();
                        maxR = curr.rating;
                    }
                }
            }
            baseOrderEnter(pairMax);
        }
    }
    
    private void updateCurrentCoinsMap() {
        updateCoinsMillis = coinInfo.getCoinsUpdateTimeMillis();
        try {
            coinInfo.getSemaphore().acquire();
            coinRatingMap.forEach((symbol, curr) -> {
                curr.do_remove_flag = true;
            });
            Set<String> allCoins = coinInfo.getCoins();
            if (coinRatingMap.isEmpty()) {
                mainApplication.getInstance().log("Found " + allCoins.size() + " coins...");
            }
            allCoins.forEach((symbol) -> {
                if (symbol != null && !symbol.isEmpty()) {
                    if (!coinRatingMap.containsKey(symbol)) {
                        CoinRatingLogItem newlog = new CoinRatingLogItem();
                        newlog.symbol = symbol.toUpperCase();
                        newlog.do_remove_flag = false;
                        newlog.update_counter = 0;
                        if (coinJSONRanks.containsKey(symbol)) {
                            newlog.rank = Integer.parseInt(coinJSONRanks.get(symbol).optString("rank", "9999"));
                            newlog.market_cap = Float.parseFloat(coinJSONRanks.get(symbol).optString("market_cap_usd", "0"));
                            newlog.fullname = coinJSONRanks.get(symbol).optString("name", symbol);
                        } else {
                            newlog.rank = 9999;
                            newlog.market_cap = 0;
                            newlog.fullname = symbol;
                        }
                        coinRatingMap.put(symbol, newlog);
                    } else {
                        coinRatingMap.get(symbol).do_remove_flag = false;
                    }
                }
            });
            List<String> listRemove = new ArrayList<>();
            coinRatingMap.forEach((symbol, curr) -> {
                if (curr.do_remove_flag) {
                    listRemove.add(symbol);
                }
            });
            listRemove.forEach((entry) -> {
                coinRatingMap.remove(entry);
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
        }
        coinInfo.getSemaphore().release();
    }
    
    private void updateCurrentPairsMap() {
        try {
            coinInfo.getSemaphore().acquire();
            coinPairRatingMap.forEach((symbol, curr) -> {
                curr.do_remove_flag = true;
            });
            if (coinPairRatingMap.isEmpty()) {
                mainApplication.getInstance().log("Found " + coinInfo.getLastPrices().size() + " pair prices...");
            }
            coinInfo.getCoinPairs().forEach((symbol, symbols) -> {
                if (!symbol.isEmpty()) {
                    double rprice = coinInfo.getLastPrices().get(symbol);
                    if (coinPairRatingMap.containsKey(symbol)) {
                        CoinRatingPairLogItem clog = coinPairRatingMap.get(symbol);
                        clog.current_price = (float) rprice;
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
                        newlog.symbol = symbol.toUpperCase();
                        newlog.symbolBase = symbols[0];
                        newlog.symbolQuote = symbols[1];
                        newlog.do_remove_flag = false;
                        newlog.current_price = (float) rprice;
                        newlog.start_price = (float) rprice;
                        newlog.percent_from_begin = 0f;
                        newlog.update_counter = 0;
                        newlog.pair = null;
                        newlog.fastbuy_skip = false;
                        if (coinRatingMap.containsKey(newlog.symbolBase)) {
                            newlog.base_rating = coinRatingMap.get(newlog.symbolBase);
                        }
                        coinPairRatingMap.put(symbol, newlog);
                    }
                }
            });
            List<String> listRemove = new ArrayList<>();
            coinPairRatingMap.forEach((symbol, curr) -> {
                if (curr.do_remove_flag) {
                    listRemove.add(symbol);
                }
            });
            listRemove.forEach((entry) -> {
                coinPairRatingMap.remove(entry);
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
        }
        coinInfo.getSemaphore().release();
    }
    
    @Override
    protected void runStart() {
        
        need_stop = false;
        paused = false;
        have_all_coins_pairs_info = false;
        
        mainApplication.getInstance().log("CoinRatingController starting...");
        
        if (coinInfo.getClient() == null) coinInfo.setClient(client);
        coinInfo.StartAndWaitForInit();

        if (!loadFromFile(serialize_filename)) {
            coinPairRatingMap = new HashMap<>();
            coinRatingMap = new HashMap<>();
            mainApplication.getInstance().log("Loading rating data...");
            loadRatingData();
            mainApplication.getInstance().log("Init coins and pairs...");
            updateCurrentCoinsMap();
            updateCurrentPairsMap();
        } else {
            mainApplication.getInstance().log("Rating data was loaded from "+serialize_filename+"...");
        }

        mainApplication.getInstance().log("Checking trend...");
        updateGlobalTrendData();
        
        if (useSignals) {
            mainApplication.getInstance().log("Starting signals controller...");
            signalOrderController.setClient(client);
            signalOrderController.start();
            while (!need_stop && !signalOrderController.isInitialSignalsLoaded()) {
                doWait(2000);
            }
        }

        if (useCycles) {
            coinCycleController.setClient(client);
            mainApplication.getInstance().log("Starting cycles controller...");
            coinCycleController.start();
        }
        
        if (analyzer) {
            mainApplication.getInstance().log("Start to collect info for coins and pairs rating...");
        }
        
        mainApplication.getInstance().log("CoinRatingController started...");
    }
    
    @Override
    protected void runBody() {

        if (have_all_coins_pairs_info || !analyzer) {
            if (updateCoinsMillis != coinInfo.getCoinsUpdateTimeMillis()) {
                updateCurrentCoinsMap();
            }
            updateCurrentPairsMap();
        }

        if (analyzer) {
            if ((System.currentTimeMillis() - updateRanksMillis) > updateRanksTime * 1000) {
                loadRatingData();
            }
            checkFromTop();
        }
        updateList();

        if (analyzer && autoOrder && have_all_coins_pairs_info) {
            checkFastEnter();
        } else if (!analyzer && entered.size() > 0) {
            checkOrderExit();
        }

        if (System.currentTimeMillis() > (updateTrendMillis + updateTrendTime*1000)) {
            updateGlobalTrendData();
        }
    }
    
    @Override
    protected void runFinish() {
        saveToFile(serialize_filename);
        if (signalOrderController != null && signalOrderController.isAlive()) signalOrderController.doStop();
        if (coinCycleController != null && coinCycleController.isAlive()) coinCycleController.doStop();
        mainApplication.getInstance().log("CoinRatingController finished.");
    }

    public DefaultListModel<String> getCoinRatingModel() {
        return coinRatingModel;
    }

    public void setClient(TradingAPIAbstractInterface _client) {
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

    /**
     * @return the minRatingForOrder
     */
    public float getMinRatingForOrder() {
        return minRatingForOrder;
    }

    /**
     * @param minRatingForOrder the minRatingForOrder to set
     */
    public void setMinRatingForOrder(float minRatingForOrder) {
        this.minRatingForOrder = minRatingForOrder;
    }
    
    public Map<String, CoinRatingPairLogItem> getCoinPairRatingMap() {
        return coinPairRatingMap;
    }
    
    public Map<String, CoinRatingPairLogItem> getEntered() {
        return entered;
    }
    
    public SignalOrderController getSignalOrderController() {
        return signalOrderController;
    }

    public CoinCycleController getCoinCycleController() {
        return coinCycleController;
    }
    
    /**
     * @return the trendUpdateEvent
     */
    public GlobalTrendUpdateEvent getTrendUpdateEvent() {
        return trendUpdateEvent;
    }

    /**
     * @param trendUpdateEvent the trendUpdateEvent to set
     */
    public void setTrendUpdateEvent(GlobalTrendUpdateEvent trendUpdateEvent) {
        this.trendUpdateEvent = trendUpdateEvent;
    }

    /**
     * @return the upTrendPercent
     */
    public double getUpTrendPercent() {
        return upTrendPercent;
    }

    /**
     * @return the downTrendPercent
     */
    public double getDownTrendPercent() {
        return downTrendPercent;
    }

    public boolean isInDownTrend() {
        return downTrendPercent > (2 * upTrendPercent) && downTrendPercent > 1;
    }
    
    /**
     * @return the noAutoBuysOnDowntrend
     */
    public boolean isNoAutoBuysOnDowntrend() {
        return noAutoBuysOnDowntrend;
    }

    /**
     * @param noAutoBuysOnDowntrend the noAutoBuysOnDowntrend to set
     */
    public void setNoAutoBuysOnDowntrend(boolean noAutoBuysOnDowntrend) {
        this.noAutoBuysOnDowntrend = noAutoBuysOnDowntrend;
    }

    /**
     * @return the useSignals
     */
    public boolean isUseSignals() {
        return useSignals;
    }

    /**
     * @param useSignals the useSignals to set
     */
    public void setUseSignals(boolean useSignals) {
        this.useSignals = useSignals;
    }

    /**
     * @return the useCycles
     */
    public boolean isUseCycles() {
        return useCycles;
    }

    /**
     * @param useCycles the useCycles to set
     */
    public void setUseCycles(boolean useCycles) {
        this.useCycles = useCycles;
    }

    /**
     * @return the paircontroller
     */
    public TradePairProcessList getPaircontroller() {
        return paircontroller;
    }

    /**
     * @return the have_all_coins_pairs_info
     */
    public boolean isHaveAllCoinsPairsInfo() {
        return have_all_coins_pairs_info;
    }

    /**
     * @return the updateTrendTime
     */
    public long getUpdateTrendTime() {
        return updateTrendTime;
    }

    /**
     * @param updateTrendTime the updateTrendTime to set
     */
    public void setUpdateTrendTime(long updateTrendTime) {
        this.updateTrendTime = updateTrendTime;
    }

    /**
     * @return the updateRanksTime
     */
    public long getUpdateRanksTime() {
        return updateRanksTime;
    }

    /**
     * @param updateRanksTime the updateRanksTime to set
     */
    public void setUpdateRanksTime(long updateRanksTime) {
        this.updateRanksTime = updateRanksTime;
    }

    /**
     * @return the updateLoadRejectTime
     */
    public long getUpdateLoadRejectTime() {
        return updateLoadRejectTime;
    }

    /**
     * @param updateLoadRejectTime the updateLoadRejectTime to set
     */
    public void setUpdateLoadRejectTime(long updateLoadRejectTime) {
        this.updateLoadRejectTime = updateLoadRejectTime;
    }
}
