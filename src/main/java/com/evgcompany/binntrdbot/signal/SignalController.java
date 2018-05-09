/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.signal;

import com.evgcompany.binntrdbot.analysis.MaxDipCriterion;
import com.evgcompany.binntrdbot.analysis.OHLC4Indicator;
import com.evgcompany.binntrdbot.analysis.TimeSeriesManagerForIndicator;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinRatingController;
import com.evgcompany.binntrdbot.mainApplication;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import com.evgcompany.binntrdbot.strategies.StrategySignal;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Decimal;
import org.ta4j.core.Order;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.NumberOfBarsCriterion;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 *
 * @author EVG_Adminer
 */
public class SignalController extends Thread {

    private class SignalsStat {
        int signals_cnt = 0;                // all signals for channel (even with wrong symbols)
        int ok_signals = 0;                 // signals with aceptable params
        int skipped_signals = 0;            // too old (>SKIP_DAYS) or > 1000 bars
        int done_signals = 0;               // signals that reached target from price1-2 (mainorder)
        int done_signals_fast = 0;          // signals that reached target from price1-2 (fastorder)
        int waiting_exit_signals = 0;       // not too old, target not reached, stoploss not reached
        int waiting_enter_signals = 0;      // not too old, not entered
        int timeout_signals = 0;            // waiting enter/exit which are old (>MAX_DAYS)
        int too_high_price_at_start = 0;    // start price > price2 or even target
        int done_price_at_start = 0;        // price > target
        int stoploss_signals = 0;           // (trailing) stoploss reached
        int stoploss_done_signals = 0;      // (trailing) stoploss reached then done
        int price1_not_set = 0;
        int price2_not_set = 0;
        int price_target_not_set = 0;
        int price_stop_not_set = 0;
        int copy_signals = 0;               // count copies from other channels
        long done_millis_diff_summ = 0;
        long copy_millis_diff_summ = 0;
        double done_summ_percent = 0;
        long done_millis_diff_summ_fast = 0;
        double done_summ_percent_fast = 0;
        double summ_percent = 0;
        double summ_percent_fast = 0;
        double summ_rating = 0;
        double avg_day_signal_profit = 0;
        double recommended_rating = 0;
        double max_done_dip_summ_percent_main = 0;
        double max_done_dip_summ_percent_mainNS = 0;
        double max_done_dip_summ_percent_fast = 0;
        double max_done_dip_summ_percent_fastNS = 0;
        Set<Integer> used_regexps = new HashSet<>();
        Set<String> copy_sources = new HashSet<>();
    }
    private final HashMap<String, SignalsStat> channel_stats = new HashMap<>();
    private final HashMap<Integer, SignalsStat> regex_stats = new HashMap<>();
    private final Set<Integer> channel_used_regexps = new HashSet<>();
    
    private final HashMap<String, List<Bar>> pair_bars = new HashMap<>();
    
    private final int MAX_DAYS = 6;
    private final int SKIP_DAYS = 30;
    private int STOPLOSS_PERCENT = 10;
    private final int NO_SIGNAL_MINUTES_TO_RESTART = 360;
    private CoinRatingController coinRatingController = null;

    private TradingAPIAbstractInterface client = null;
    private String signalsListenerFile;
    private String apiId = "";
    private String apiHash = "";
    private String apiPhone = "";
    private StartedProcess signalsProcess = null;
    private boolean isChecking = false;
    private final LinkedList<String> newlines = new LinkedList<>();
    private List<SignalItem> items = new ArrayList<>(0);
    
    private SignalEvent signalEvent = null;
    private boolean initialSignalsIsLoaded = false;
    
    private long lastProcessActivityMillis;

    private int preload_count = 200;
    
    private static final DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SignalController() {
        signalsListenerFile = "TGsignalsListener.py";
        lastProcessActivityMillis = System.currentTimeMillis();
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        stopSignalsProcessIfRunning();
    }

    public void stopSignalsProcessIfRunning() {
        if (signalsProcess != null && signalsProcess.getProcess().isAlive()) {
            signalsProcess.getProcess().destroy();
            signalsProcess = null;
            initialSignalsIsLoaded = true;
        }
    }

    private void doWait(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }
    
    @Override
    public void run() {
        if (mainApplication.getInstance().getOrdersController() != null && mainApplication.getInstance().getOrdersController().getStopLossPercent() != null) {
            STOPLOSS_PERCENT = mainApplication.getInstance().getOrdersController().getStopLossPercent().intValue();
        }
        initialSignalsIsLoaded = false;
        while (signalsProcess != null && signalsProcess.getProcess().isAlive()) {
            doWait(500);
            if (!newlines.isEmpty()) {
                onSignalNewLine(newlines.removeFirst());
            } else if ((System.currentTimeMillis() - lastProcessActivityMillis) > (NO_SIGNAL_MINUTES_TO_RESTART * 60000)) {
                restartSignalsProcess();
            }
        }
        if (signalsProcess != null) {
            stopSignalsProcess();
        }
        initialSignalsIsLoaded = true;
    }
    
    private void initMainProcess(int signals_preload_count) throws IOException {
        lastProcessActivityMillis = System.currentTimeMillis();
        signalsProcess = new ProcessExecutor()
            .command("python", "-u", signalsListenerFile, apiId, apiHash, apiPhone, String.valueOf(signals_preload_count))
            .redirectOutput(new LogOutputStream() {
                @Override
                protected void processLine(String line) {
                    System.out.println(line);
                    newlines.addLast(line);
                    lastProcessActivityMillis = System.currentTimeMillis();
                }
            })
            .redirectError(new LogOutputStream() {
                @Override
                protected void processLine(String line) {
                    System.out.println(line);
                    mainApplication.getInstance().log("Error: " + line);
                }
            })
            .redirectInput(System.in)
            .start();
    }
    
    public void startSignalsProcess(boolean isChecking, int signals_preload_count) {
        if (signalsProcess != null || isAlive()) {
            return;
        }
        try {
            initialSignalsIsLoaded = false;
            mainApplication.getInstance().log("Starting process: " + signalsListenerFile);
            this.isChecking = isChecking;
            initMainProcess(signals_preload_count);
            start();
        } catch (IOException | InvalidExitValueException ex) {
            mainApplication.getInstance().log(ex.getMessage());
            initialSignalsIsLoaded = true;
        }
    }
    public void startSignalsProcess(boolean isChecking) {
        startSignalsProcess(isChecking, preload_count);
    }
    public void startSignalsProcess() {
        startSignalsProcess(false, preload_count);
    }
    
    public void stopSignalsProcess() {
        signalsProcess.getProcess().destroy();
        signalsProcess = null;
        initialSignalsIsLoaded = true;
        mainApplication.getInstance().log("Stopping process: " + signalsListenerFile);
    }

    public void restartSignalsProcess() {
        if (isChecking || !initialSignalsIsLoaded || signalsProcess == null || !isAlive()) {
            return;
        }
        mainApplication.getInstance().log("Restarting process: " + signalsListenerFile);
        signalsProcess.getProcess().destroy();
        try {
            doWait(500);
            initMainProcess(0);
        } catch (IOException | InvalidExitValueException ex) {
            mainApplication.getInstance().log(ex.getMessage());
        }
    }

    public SignalItem getBestSignalToReEnter(double min_current_rating) {
        double best_rating = 0;
        SignalItem best_item = null;
        for (SignalItem item : items) {
            if (item.isAutopick() && !item.isDone() && !item.isTimeout()) {
                double c_rating = item.getCurrentRating();
                if (c_rating > min_current_rating) {
                    if (coinRatingController == null || 
                        !coinRatingController.isAlive() ||
                        coinRatingController.getCoinPairRatingMap().isEmpty() ||
                        !coinRatingController.getCoinPairRatingMap().containsKey(item.getPair())
                    ) {
                        item.updateDoneAndTimeout();
                        if (!item.isDone() && !item.isTimeout()) {
                            if (item.isPriceFromAuto()) c_rating-=0.3;
                            if (item.isPriceToAuto()) c_rating-=0.2;
                            if (item.isPriceTargetAuto()) c_rating-=0.3;
                            if (item.isPriceStoplossAuto()) c_rating-=0.1;
                            if (best_rating < c_rating) {
                                best_rating = c_rating;
                                best_item = item;
                            }
                        }
                    }
                }
            }
        }
        return best_item;
    }

    public void inputStr(String str) {
        try {
            OutputStream stdin = signalsProcess.getProcess().getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
            writer.write(str);
            writer.write("\n");
            writer.flush();
        } catch (IOException ex) {
            Logger.getLogger(SignalController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private Double strPrice(String str) {
        if (str == null || str.isEmpty() || Double.parseDouble(str) <= 0) {
            return null;
        }
        return Double.parseDouble(str);
    }
    
    private void onSignalNewLine(String line) {
        if (line.startsWith("Enter the code (")) {
            inputStr(queryCode(line));
            return;
        }
        if (line.startsWith("-----------------")) {
            if (!initialSignalsIsLoaded) {
                initialSignalsIsLoaded = true;
                if (isChecking) {
                    stopSignalsProcess();
                } else {
                    pair_bars.clear();
                    checkSignalItems();
                    logStats();
                    sendPreviousSignals();
                }
            }
            return;
        }
        String[] parts = line.split(";");
        if (parts.length != 10) {
            return;
        }
        if (!isChecking) {
            mainApplication.getInstance().log(line);
            addSignal(
                parts[2], // symbol1
                parts[3], // symbol2
                LocalDateTime.parse(parts[1], df), // datetime
                Double.parseDouble(parts[0]), // rating
                strPrice(parts[4]), // price1
                strPrice(parts[5]), // price2
                strPrice(parts[6]),  // price_target
                strPrice(parts[7]),  // price_stoploss
                Integer.parseInt(parts[8]),
                parts[9] // channel title
            );
        } else {
            mainApplication.getInstance().log(line);
        }
    }
    
    private void showStatInfo(SignalsStat stat) {
        long avg_time = 0;
        double avg_done_percent = 0;
        long avg_time_fast = 0;
        double avg_done_percent_fast = 0;
        double avg_percent = 0;
        double avg_percent_fast = 0;
        double avg_rating = 0;
        double avg_max_done_dip_percent = 0;
        double avg_copy_diff = 0;
        if (stat.done_signals > 0) {
            avg_time = stat.done_millis_diff_summ / stat.done_signals;
            avg_done_percent = stat.done_summ_percent / stat.done_signals;
            avg_max_done_dip_percent = stat.max_done_dip_summ_percent_main / stat.ok_signals;
        }
        if (stat.done_signals_fast > 0) {
            avg_time_fast = stat.done_millis_diff_summ_fast / stat.done_signals_fast;
            avg_done_percent_fast = stat.done_summ_percent_fast / stat.done_signals_fast;
        }
        if (stat.ok_signals > 0) {
            avg_percent = stat.summ_percent / stat.ok_signals;
            avg_percent_fast = stat.summ_percent_fast / stat.ok_signals;
            avg_rating = stat.summ_rating / stat.ok_signals;
        }
        if (stat.copy_signals > 0) {
            avg_copy_diff = stat.copy_millis_diff_summ / stat.copy_signals;
        }
        mainApplication.getInstance().log(
            "All / Skipped / Normal Signals count: " + stat.signals_cnt + " / " + stat.skipped_signals + " / " + stat.ok_signals + "\n" +
            "Signals with current price > price2 / price > target at start: " + stat.too_high_price_at_start + " / " + stat.done_price_at_start + "\n" +
            "Waiting for enter / exit signals: " + stat.waiting_enter_signals + " / " + stat.waiting_exit_signals + "\n" + 
            "Timeout signals (waiting for more than "+MAX_DAYS+"d): " + stat.timeout_signals + "\n" +
            "Stoploss signals (default: -"+STOPLOSS_PERCENT+"%): " + stat.stoploss_signals + "\n" +
            "Stoploss then done signals: " + stat.stoploss_done_signals + "\n" + 
            "Signals without price1 / price2 / target / stoploss: " + stat.price1_not_set + " / " + stat.price2_not_set + " / " + stat.price_target_not_set + " / " + stat.price_stop_not_set + "\n" + 
            "Done signals (main / fast): " + stat.done_signals + " / " + stat.done_signals_fast + "\n" + 
            "Done signals avg time (main / fast): " + NumberFormatter.df8.format(avg_time / 86400000.0) + " / " + NumberFormatter.df8.format(avg_time_fast / 86400000.0) + " day.\n" + 
            "Done signals avg percent profit (main / fast): " + NumberFormatter.df8.format(avg_done_percent) + " / " + NumberFormatter.df8.format(avg_done_percent_fast) + "%\n" + 
            "Avg signal profit (main / fast): " + NumberFormatter.df8.format(avg_percent) + " / " + NumberFormatter.df8.format(avg_percent_fast) + "%\n" + 
            "Avg rating: " + NumberFormatter.df8.format(avg_rating) + "\n" + 
            "Avg MAX Dip percent: " + NumberFormatter.df8.format(avg_max_done_dip_percent) + "%\n" + 
            "Copy avg interval: " + NumberFormatter.df8.format(avg_copy_diff / 60000) + " min.\n" + 
            "Copy signals: " + stat.copy_signals + "\n" + 
            "Channel copy sources: " + stat.copy_sources + "\n" + 
            "Channel regexp_ids: " + stat.used_regexps + "\n" + 
            "Average day signal profit: " + NumberFormatter.df8.format(stat.avg_day_signal_profit) + "%\n" + 
            "Recommended rating: " + NumberFormatter.df8.format(stat.recommended_rating) + "\n" + 
            "\n"
        );
    }
    
    private void logStats() {
        calculateRecommendedRatings(channel_stats.values());
        calculateRecommendedRatings(regex_stats.values());
        mainApplication.getInstance().log("");
        mainApplication.getInstance().log("Channel stats:\n");
        for (Map.Entry<String, SignalsStat> entry : channel_stats.entrySet()) {
            if (!entry.getKey().isEmpty() && !entry.getKey().equals("-")) {
                mainApplication.getInstance().log("Channel '" + entry.getKey() + "' Stats:");
                showStatInfo(entry.getValue());
            }
        }
        mainApplication.getInstance().log("Set of all channels regexp_ids: " + channel_used_regexps.toString());
        mainApplication.getInstance().log("");
        mainApplication.getInstance().log("");
        mainApplication.getInstance().log("Regex stats:\n");
        for (Map.Entry<Integer, SignalsStat> entry : regex_stats.entrySet()) {
            if (entry.getKey() > 0) {
                mainApplication.getInstance().log("Regex '" + entry.getKey() + "' Stats:");
                showStatInfo(entry.getValue());
            }
        }
        mainApplication.getInstance().log("");
    }
    
    private void sendPreviousSignals() {
        if (signalEvent == null) return;
        for (SignalItem item : items) {
            if (!item.isDone() && 
                !item.isTimeout() && 
                item.isAutopick() &&
                item.getMillisFromSignalStart() >= 0 &&
                item.getMillisFromSignalStart() < 2 * 60 * 60 * 1000
            ) {
                signalEvent.onUpdate(item, getPairSignalRating(item.getPair()));
            }
        }
    }
    
    private Double alignPrice(Double price, Double current) {
        if (price > 0 && current > 0) {
            int i = 0;
            while ((++i < 20) && (Math.abs(price - current) / current > 5)) {
                price *= 0.1;
            }
            return price;
        }
        return 0.0;
    }
    
    private int getMillisIndex(List<Bar> bars, long millisFrom) {
        if (bars.get(0).getBeginTime().toInstant().toEpochMilli() > millisFrom) {
            return -1;
        }
        int i;
        for(i = 0; i < bars.size() && bars.get(i).getEndTime().toInstant().toEpochMilli() < millisFrom; i++){}
        return i;
    }
    
    private Strategy getSignalStrategy(TimeSeries series, boolean is_fast, BigDecimal price1, BigDecimal price2, BigDecimal price_target, BigDecimal price_stop) {
        StrategySignal sig = new StrategySignal(null);
        sig.setOrdersController(mainApplication.getInstance().getOrdersController());
        sig.getConfig().setParam("Price1", price1);
        sig.getConfig().setParam("Price2", price2);
        sig.getConfig().setParam("PriceTarget", price_target);
        sig.getConfig().setParam("PriceStop", price_stop);
        sig.getConfig().setParam("FastOrder", BigDecimal.valueOf(is_fast ? 1 : 0));
        sig.getConfig().setParam("OrderOnce", BigDecimal.valueOf(1));
        Strategy result = sig.buildStrategy(series);
        return result;
    }
    
    private void addSignal(String symbol1, String symbol2, LocalDateTime datetime, double rating, Double price1, Double price2, Double price_target, Double price_stoploss, int exp_id, String title) {
        SignalsStat chstat = null;
        SignalsStat rxstat = null;
        if (title != null && !title.isEmpty()) {
            if (!channel_stats.containsKey(title)) {
                chstat = new SignalsStat();
                channel_stats.put(title, chstat);
            } else {
                chstat = channel_stats.get(title);
            }
        }
        if (exp_id > 0) {
            if (!regex_stats.containsKey(exp_id)) {
                rxstat = new SignalsStat();
                regex_stats.put(exp_id, rxstat);
            } else {
                rxstat = regex_stats.get(exp_id);
            }
        }
        SignalsStat stats[] = {chstat, rxstat};
        for (SignalsStat stat : stats) {
            if (stat != null) {
                stat.signals_cnt++;
                if (price1 == null) stat.price1_not_set++;
                if (price2 == null) stat.price2_not_set++;
                if (price_target == null) stat.price_target_not_set++;
                if (price_stoploss == null) stat.price_stop_not_set++;
                stat.used_regexps.add(exp_id);
            }
        }
        channel_used_regexps.add(exp_id);
        if (symbol1 == null || symbol1.isEmpty() || datetime == null || rating == 0 || (price_target == null && price1 == null && price2 == null)) {
            return;
        }
        if (symbol2 == null || symbol2.isEmpty()) {
            symbol2 = "BTC";
        }
        symbol1 = symbol1.toUpperCase();
        symbol2 = symbol2.toUpperCase();
        String symbol_pair = symbol1 + symbol2;
        if (coinRatingController != null && !coinRatingController.getCoinPairRatingMap().isEmpty() && !coinRatingController.getCoinPairRatingMap().containsKey(symbol_pair)) {
            return;
        }
        List<Bar> bars = null;
        double barMinutes = 5;
        if (!initialSignalsIsLoaded && pair_bars.containsKey(symbol_pair)) {
            bars = pair_bars.get(symbol_pair);
        }
        if (bars == null) {
            try {
                if (initialSignalsIsLoaded) {
                    bars = client.getBars(symbol_pair, "5m", 1000, 1);
                } else {
                    bars = client.getBars(symbol_pair, "5m", 1000, 8);
                    pair_bars.put(symbol_pair, bars);
                }
            } catch(Exception e) {}
        }
        
        if (bars == null || bars.isEmpty()) {
            return;
        }

        boolean is_skipped = LocalDateTime.now().minusDays(SKIP_DAYS).isAfter(datetime) || datetime.isBefore(bars.get(0).getBeginTime().toLocalDateTime());
        if (is_skipped) {
            for (SignalsStat stat : stats) {
                if (stat != null) stat.skipped_signals++;
            }
            return;
        }

        boolean price1_a = false;
        boolean price2_a = false;
        boolean priceT_a = false;
        boolean priceS_a = false;
        long millis_signal = datetime.toInstant(OffsetDateTime.now().getOffset()).toEpochMilli();
        int millis_index = getMillisIndex(bars, millis_signal);
        double enter_price;
        if (millis_index >= 0 && millis_index < bars.size()) {
            enter_price = bars.get(millis_index).getClosePrice().doubleValue();
        } else if (millis_index >= bars.size()) {
            enter_price = bars.get(bars.size()-1).getClosePrice().doubleValue();
        } else {
            enter_price = bars.get(0).getClosePrice().doubleValue();
        }

        if (price2 == null && price_target != null) {
            price2 = price_target * 0.95;
        } else if (price1 != null && price2 == null) {
            price2 = price1;
            price1 = price2 * 0.95;
            price2_a = true;
        } else if (price1 == null && price2 != null) {
            price1 = price2 * 0.95;
            price1_a = true;
        }
        if (price1 == null) {
            price1 = enter_price * 0.95;
            price1_a = true;
        }
        if (price2 == null || Objects.equals(price2, price1)) {
            price2 = price1 * 1.05;
            price2_a = true;
        }
        if (price_target == null) {
            price_target = price2 * 1.05;
            priceT_a = true;
        }

        Double currPrice = bars.get(bars.size()-1).getClosePrice().doubleValue();
        price1 = alignPrice(price1, currPrice);
        price2 = alignPrice(price2, currPrice);
        price_target = alignPrice(price_target, currPrice);
        
        if (price_stoploss == null) {
            price_stoploss = Math.min(price1, currPrice) * (100-STOPLOSS_PERCENT) / 100;
            priceS_a = true;
        }
        price_stoploss = alignPrice(price_stoploss, currPrice);

        if (price1 > enter_price * 2.5
                || price2 > enter_price * 3.0
                || price_target > enter_price * 3.5
                || price1 < enter_price / 2.5
                || price2 < enter_price / 2.0
                || price_target < enter_price / 1.5) {
            System.out.println("Signal " + symbol_pair + ": suspicious prices. Skipping...");
            for (SignalsStat stat : stats) {
                if (stat != null) stat.skipped_signals++;
            }
            return;
        }

        if (price1 > price_target) {
            price1 = price_target * 0.9;
            price1_a = true;
        }
        if (price2 > price_target) {
            price2 = price_target * 0.95;
            price2_a = true;
        }
        if (price1 > price2) {
            double tmp = price1;
            price1 = price2;
            price2 = tmp;
            boolean tmpB = price1_a;
            price1_a = price2_a;
            price2_a = tmpB;
        }
        if (price_stoploss > price1) {
            price_stoploss = price1 * (100-STOPLOSS_PERCENT) / 100;
            priceS_a = true;
        } else if (price_stoploss < price1 * 0.75) {
            price_stoploss = price1 * 0.75;
            priceS_a = true;
        }

        double max_order_price = (price2*90+price_target*10)/100;
        
        boolean is_done = false;
        boolean is_done_fast = false;
        boolean is_waiting_exit = false;
        boolean is_waiting_exitF = false;
        boolean is_waiting_exitNS = false;
        boolean is_waiting_exitMNS = false;
        boolean is_waiting_enter = false;
        boolean is_stop_loss = false;
        boolean is_stop_loss_done = false;
        boolean is_price_too_high = enter_price >= max_order_price;
        boolean is_done_at_start = enter_price >= price_target;
        boolean is_timeout = false;
        
        if (is_done_at_start) {
            for (SignalsStat stat : stats) {
                if (stat != null) stat.done_price_at_start++;
            }
            is_done = true;
            is_timeout = true;
        } else if (millis_index  >= 0 && millis_index < bars.size()) {
            TimeSeries series = new BaseTimeSeries("STAT_SERIES");
            TradingAPIAbstractInterface.addSeriesBars(series, bars);
            TotalProfitCriterion profitCriterion = new TotalProfitCriterion();
            NumberOfBarsCriterion barsCountCriterion = new NumberOfBarsCriterion();
            MaxDipCriterion maxDipCriterion = new MaxDipCriterion();
            OHLC4Indicator indicator = new OHLC4Indicator(series);

            Strategy strategy_main = getSignalStrategy(series, false, new BigDecimal(price1), new BigDecimal(price2), new BigDecimal(price_target), new BigDecimal(price_stoploss));
            Strategy strategy_main_nostop = getSignalStrategy(series, false, new BigDecimal(price1), new BigDecimal(price2), new BigDecimal(price_target), BigDecimal.ZERO);
            Strategy strategy_fast = getSignalStrategy(series, true, new BigDecimal(price1), new BigDecimal(price2), new BigDecimal(price_target), new BigDecimal(price_stoploss));
            Strategy strategy_fast_nostop = getSignalStrategy(series, true, new BigDecimal(price1), new BigDecimal(price2), new BigDecimal(price_target), BigDecimal.ZERO);
            TimeSeriesManagerForIndicator seriesManager = new TimeSeriesManagerForIndicator(series, indicator);
            TradingRecord tradingRecordMain = seriesManager.run(strategy_main, Order.OrderType.BUY, millis_index, series.getEndIndex());
            TradingRecord tradingRecordMainNS = seriesManager.run(strategy_main_nostop, Order.OrderType.BUY, millis_index, series.getEndIndex());
            TradingRecord tradingRecordFast = seriesManager.run(strategy_fast, Order.OrderType.BUY, millis_index, series.getEndIndex());
            TradingRecord tradingRecordFastNS = seriesManager.run(strategy_fast_nostop, Order.OrderType.BUY, millis_index, series.getEndIndex());

            System.out.println();
            System.out.println(symbol_pair + " " + datetime + " " + NumberFormatter.df8.format(price1)+" " + NumberFormatter.df8.format(price2) + " " + NumberFormatter.df8.format(price_target) + " " + NumberFormatter.df8.format(price_stoploss) + "    " + millis_index + "/" + bars.size() + " -- " + bars.get(millis_index).getBeginTime());
            
            if (millis_index < series.getEndIndex()) {
                if (tradingRecordMain.getLastEntry() != null && tradingRecordMain.getLastExit() == null) {
                    tradingRecordMain.exit(series.getEndIndex(), indicator.getValue(series.getEndIndex()), Decimal.NaN);
                    System.out.println("Close main order on last index.");
                    is_waiting_exit = true;
                }
                if (tradingRecordMainNS.getLastEntry() != null && tradingRecordMainNS.getLastExit() == null) {
                    tradingRecordMainNS.exit(series.getEndIndex(), indicator.getValue(series.getEndIndex()), Decimal.NaN);
                    System.out.println("Close main NS order on last index.");
                    is_waiting_exitMNS = true;
                }
                if (tradingRecordFast.getLastEntry() != null && tradingRecordFast.getLastExit() == null) {
                    tradingRecordFast.exit(series.getEndIndex(), indicator.getValue(series.getEndIndex()), Decimal.NaN);
                    System.out.println("Close fast order on last index.");
                    is_waiting_exitF = true;
                }
                if (tradingRecordFastNS.getLastEntry() != null && tradingRecordFastNS.getLastExit() == null) {
                    tradingRecordFastNS.exit(series.getEndIndex(), indicator.getValue(series.getEndIndex()), Decimal.NaN);
                    System.out.println("Close fast NS order on last index.");
                    is_waiting_exitNS = true;
                }
            } else {
                if (tradingRecordMain.getTradeCount() > 0) tradingRecordMain.getTrades().clear();
                if (tradingRecordMainNS.getTradeCount() > 0) tradingRecordMainNS.getTrades().clear();
                if (tradingRecordFast.getTradeCount() > 0) tradingRecordFast.getTrades().clear();
                if (tradingRecordFastNS.getTradeCount() > 0) tradingRecordFastNS.getTrades().clear();
            }
            
            double profit_main = profitCriterion.calculate(series, tradingRecordMain);
            double profit_mainNS = profitCriterion.calculate(series, tradingRecordMainNS);
            double profit_fast = profitCriterion.calculate(series, tradingRecordFast);
            double profit_fastNS = profitCriterion.calculate(series, tradingRecordFastNS);
            double barscnt_main = barsCountCriterion.calculate(series, tradingRecordMain);
            double barscnt_mainNS = barsCountCriterion.calculate(series, tradingRecordMainNS);
            double barscnt_fast = barsCountCriterion.calculate(series, tradingRecordFast);
            double barscnt_fastNS = barsCountCriterion.calculate(series, tradingRecordFastNS);
            double maxdip_main = maxDipCriterion.calculate(series, tradingRecordMain);
            double maxdip_mainNS = maxDipCriterion.calculate(series, tradingRecordMainNS);
            double maxdip_fast = maxDipCriterion.calculate(series, tradingRecordFast);
            double maxdip_fastNS = maxDipCriterion.calculate(series, tradingRecordFastNS);
            
            System.out.println(tradingRecordMain.getTrades());
            System.out.println("M   Profit:" + profit_main + "   Bars:" + barscnt_main + "   Trades:" + tradingRecordMain.getTradeCount() + "    Maxdip:" + NumberFormatter.df8.format(maxdip_main)+"%");
            System.out.println(tradingRecordMainNS.getTrades());
            System.out.println("MNS Profit:" + profit_mainNS + "   Bars:" + barscnt_mainNS + "   Trades:" + tradingRecordMainNS.getTradeCount() + "    Maxdip:" + NumberFormatter.df8.format(maxdip_mainNS)+"%");
            System.out.println(tradingRecordFast.getTrades());
            System.out.println("F   Profit:" + profit_fast + "   Bars:" + barscnt_fast + "   Trades:" + tradingRecordFast.getTradeCount() + "    Maxdip:" + NumberFormatter.df8.format(maxdip_fast)+"%");
            System.out.println(tradingRecordFastNS.getTrades());
            System.out.println("NS  Profit:" + profit_fastNS + "   Bars:" + barscnt_fastNS + "   Trades:" + tradingRecordFastNS.getTradeCount() + "    Maxdip:" + NumberFormatter.df8.format(maxdip_fastNS)+"%");
            System.out.println();
            
            is_waiting_enter = tradingRecordMain.getTradeCount() == 0;
            is_done = !is_waiting_enter && !is_waiting_exit && profit_main > 1;
            is_done_fast = !is_waiting_exitF && profit_fast > 1;
            is_stop_loss = !is_waiting_enter && !is_waiting_exit && profit_main < 1;
            is_stop_loss_done = !is_waiting_exitF && !is_waiting_exitNS && barscnt_fast < 1 && profit_fastNS > 1;
            is_timeout = (is_waiting_enter || is_waiting_exit) && LocalDateTime.now().minusDays(MAX_DAYS).isAfter(datetime);
            is_timeout = is_timeout || (!is_waiting_enter && !is_waiting_exit && barscnt_main > MAX_DAYS * 24 * 60 / barMinutes);
            
            for (SignalsStat stat : stats) {
                if (stat != null) {
                    stat.ok_signals++;
                    if (is_done) stat.done_signals++;
                    if (is_done_fast) stat.done_signals_fast++;
                    if (is_stop_loss) stat.stoploss_signals++;
                    if (is_price_too_high) stat.too_high_price_at_start++;
                    if (is_timeout) stat.timeout_signals++;
                    if (is_waiting_exit) stat.waiting_exit_signals++;
                    if (is_waiting_enter) stat.waiting_enter_signals++;
                    if (is_stop_loss_done) stat.stoploss_done_signals++;
                    if (is_done) {
                        stat.done_millis_diff_summ += barscnt_main * barMinutes * 60 * 1000;
                        stat.done_summ_percent += 100*(profit_main-1);
                        stat.max_done_dip_summ_percent_main += maxdip_main;
                        /*stat.max_done_dip_summ_percent_mainNS += maxdip_mainNS;
                        stat.max_done_dip_summ_percent_fast += maxdip_fast;
                        stat.max_done_dip_summ_percent_fastNS += maxdip_fast;*/
                    }
                    if (is_done_fast) {
                        stat.done_millis_diff_summ_fast += barscnt_fast * barMinutes * 60 * 1000;
                        stat.done_summ_percent_fast += 100*(profit_fast-1);
                    }
                    stat.summ_percent += 100*(profit_main-1);
                    stat.summ_percent_fast += 100*(profit_fast-1);
                    stat.summ_rating += rating;
                }
            }

        } else {
            System.out.println();
            System.out.println(millis_index);
            System.out.println();
            for (SignalsStat stat : stats) {
                if (stat != null) stat.skipped_signals++;
            }
        }
        
        SignalItem s = new SignalItem();
        s.setMaxDaysAgo(MAX_DAYS);
        s.setDatetime(datetime);
        s.setLocalMillis(millis_signal);
        s.setSymbol1(symbol1);
        s.setSymbol2(symbol2);
        s.setPriceFrom(new BigDecimal(price1));
        s.setPriceTo(new BigDecimal(price2));
        s.setPriceTarget(new BigDecimal(price_target));
        s.setPriceStoploss(new BigDecimal(price_stoploss));
        s.setPriceFromAuto(price1_a);
        s.setPriceToAuto(price2_a);
        s.setPriceTargetAuto(priceT_a);
        s.setPriceStoplossAuto(priceS_a);
        s.setBaseRating(rating);
        s.setDone(is_done);
        s.setTimeout(is_timeout);
        s.setChannelName(title);
        s.updateHash();
        items.add(s);

        if (initialSignalsIsLoaded) {
            checkSignalItems();
        }

        boolean is_copy = !items.contains(s);
        
        mainApplication.getInstance().log(
            "Signal "
            + symbol_pair + " from " 
            + datetime.format(df) + " (channel = '"+title+"') " 
            + "rating = " + rating + "; "
            + NumberFormatter.df8.format(price1) + " - " 
            + NumberFormatter.df8.format(price2) + " ===> " 
            + NumberFormatter.df8.format(price_target)
            + "; Stoploss: " + NumberFormatter.df8.format(price_stoploss) + " : "
            + (is_done ? "DONE " : " ")
            + (is_stop_loss ? "STOP " : " ")
            + (is_timeout ? "TIMEOUT " : "")
            + (is_waiting_enter ? "WAIT_ENTER " : "")
            + (is_waiting_exit ? "WAIT_EXIT " : "")
            + (is_copy ? "COPY" : "")
        );
        
        if (signalEvent != null && !is_copy) {
            signalEvent.onUpdate(s, getPairSignalRating(s.getPair()));
        }
    }

    private void calculateRecommendedRatings(Collection<SignalsStat> stats) {
        double min_avg_day_profit = Integer.MAX_VALUE;
        double max_avg_day_profit = Integer.MIN_VALUE;
        double min_ok_signals = Integer.MAX_VALUE;
        double max_ok_signals = Integer.MIN_VALUE;
        
        for (SignalsStat stat : stats) {
            long avg_time = 0;
            long avg_time_fast = 0;
            double avg_percent = 0;
            double avg_percent_fast = 0;
            double avg_day_signal_profit = 0;
            if (stat.done_signals > 0) {
                avg_time = stat.done_millis_diff_summ / stat.done_signals;
            }
            if (stat.done_signals_fast > 0) {
                avg_time_fast = stat.done_millis_diff_summ_fast / stat.done_signals_fast;
            }
            if (stat.ok_signals > 0) {
                avg_percent = stat.summ_percent / stat.ok_signals;
                avg_percent_fast = stat.summ_percent_fast / stat.ok_signals;
                if (avg_time > 0) {
                    avg_day_signal_profit = avg_percent / ((double) avg_time / ((double) 1000 * 60 * 60 * 24));
                } else if (avg_time_fast > 0) {
                    avg_day_signal_profit = avg_percent_fast / ((double) avg_time_fast / ((double) 1000 * 60 * 60 * 24));
                } else {
                    avg_day_signal_profit = avg_percent;
                }
            }
            stat.avg_day_signal_profit = avg_day_signal_profit;
            if (min_ok_signals > stat.ok_signals) min_ok_signals = stat.ok_signals;
            if (max_ok_signals < stat.ok_signals) max_ok_signals = stat.ok_signals;
            if (min_avg_day_profit > avg_day_signal_profit) min_avg_day_profit = avg_day_signal_profit;
            if (max_avg_day_profit < avg_day_signal_profit) max_avg_day_profit = avg_day_signal_profit;
            stat.recommended_rating = 5;
        }

        if (max_ok_signals <= 0 || max_avg_day_profit <= -100) {
            return;
        }
        
        double avg_for_sub_half = min_avg_day_profit*0.75 + max_avg_day_profit * 0.25;
        double avg_for_add_half = min_avg_day_profit*0.25 + max_avg_day_profit * 0.75;
        double avg_diff = max_avg_day_profit - min_avg_day_profit;
        
        double ok_for_min = 5;
        
        for (SignalsStat stat : stats) {
            if (stat.ok_signals <= ok_for_min && stat.avg_day_signal_profit < avg_for_sub_half) {
                stat.recommended_rating = 2;
            } else if (stat.ok_signals <= ok_for_min && stat.avg_day_signal_profit > avg_for_add_half) {
                stat.recommended_rating = 7;
            } else if (stat.ok_signals > ok_for_min) {
                stat.recommended_rating = 9 * ((stat.avg_day_signal_profit - min_avg_day_profit) / avg_diff) + 1;
            }
            if (stat.ok_signals > 0) {
                if (stat.stoploss_signals < 0.2 * stat.ok_signals) stat.recommended_rating += 1;
                if (stat.stoploss_signals < 0.05 * stat.ok_signals) stat.recommended_rating += 1;
                if (stat.stoploss_signals > 0.4 * stat.ok_signals) stat.recommended_rating -= 1;
                if (stat.stoploss_signals > 0.6 * stat.ok_signals) stat.recommended_rating -= 1;
                if (stat.stoploss_signals > 0.8 * stat.ok_signals) stat.recommended_rating -= 1;
            }
            if (stat.recommended_rating < 1) stat.recommended_rating = 1;
            else if (stat.recommended_rating > 10) stat.recommended_rating = 10;
        }
    }
    
    private void checkSignalItems() {
        List<SignalItem> to_remove = new ArrayList<>(0);
        for(int i=0; i < items.size(); i++) {
            if (!to_remove.contains(items.get(i))) {
                for(int j=0; j < items.size(); j++) {
                    if (i != j && !to_remove.contains(items.get(j))) {
                        if (items.get(i).getHash().equals(items.get(j).getHash())) {
                            if (items.get(i).getDatetime().isAfter(items.get(j).getDatetime())) {
                                if ((items.get(i).getLocalMillis() - items.get(j).getLocalMillis()) < MAX_DAYS * 24 * 60 * 60 * 1000) {
                                    to_remove.add(items.get(i));
                                    items.get(j).mergeBaseRating(items.get(i).getBaseRating());
                                    if (channel_stats.containsKey(items.get(i).getChannelName())) {
                                        channel_stats.get(items.get(i).getChannelName()).copy_signals++;
                                        channel_stats.get(items.get(i).getChannelName()).copy_sources.add(items.get(j).getChannelName());
                                        channel_stats.get(items.get(i).getChannelName()).copy_millis_diff_summ += items.get(i).getLocalMillis() - items.get(j).getLocalMillis();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        items = items.stream()
            .filter(p -> !to_remove.contains(p))
            .collect(Collectors.toList());
    }
    
    public double getPairSignalRating(String symbol) {
        double result = 0;
        for(int i = 0; i < items.size(); i++) {
            if (items.get(i).getPair().equals(symbol) || items.get(i).getSymbol1().equals(symbol)) {
                result = result + items.get(i).getCurrentRating();
            }
        }
        return result;
    }
    
    private String queryCode(String text) {
        return JOptionPane.showInputDialog(
            mainApplication.getInstance(),
            text,
            "Input",
            JOptionPane.QUESTION_MESSAGE
        );
    }
    
    /**
     * @return the client
     */
    public TradingAPIAbstractInterface getClient() {
        return client;
    }

    /**
     * @param client the client to set
     */
    public void setClient(TradingAPIAbstractInterface client) {
        this.client = client;
    }

    /**
     * @return the signalsListenerFile
     */
    public String getSignalsListenerFile() {
        return signalsListenerFile;
    }

    /**
     * @param signalsListenerFile the TgsignalsListener to set
     */
    public void setSignalsListenerFile(String signalsListenerFile) {
        this.signalsListenerFile = signalsListenerFile;
    }

    /**
     * @return the apiId
     */
    public String getApiId() {
        return apiId;
    }

    /**
     * @param apiId the apiId to set
     */
    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    /**
     * @return the apiHash
     */
    public String getApiHash() {
        return apiHash;
    }

    /**
     * @param apiHash the apiHash to set
     */
    public void setApiHash(String apiHash) {
        this.apiHash = apiHash;
    }

    /**
     * @return the apiPhone
     */
    public String getApiPhone() {
        return apiPhone;
    }

    /**
     * @param apiPhone the apiPhone to set
     */
    public void setApiPhone(String apiPhone) {
        this.apiPhone = apiPhone;
    }

    /**
     * @param signalEvent the signalEvent to set
     */
    public void setSignalEvent(SignalEvent signalEvent) {
        this.signalEvent = signalEvent;
    }

    /**
     * @return the initialSignalsIsLoaded
     */
    public boolean isInitialSignalsLoaded() {
        return initialSignalsIsLoaded;
    }

    /**
     * @return the preload_count
     */
    public int getPreloadCount() {
        return preload_count;
    }

    /**
     * @param preload_count the preload_count to set
     */
    public void setPreloadCount(int preload_count) {
        this.preload_count = preload_count;
    }

    /**
     * @return the coinRatingController
     */
    public CoinRatingController getCoinRatingController() {
        return coinRatingController;
    }

    /**
     * @param coinRatingController the coinRatingController to set
     */
    public void setCoinRatingController(CoinRatingController coinRatingController) {
        this.coinRatingController = coinRatingController;
    }
}
