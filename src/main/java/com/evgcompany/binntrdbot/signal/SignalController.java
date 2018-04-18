/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.signal;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.mainApplication;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 *
 * @author EVG_Adminer
 */
public class SignalController extends Thread {

    private class ChannelStat {
        int signals_cnt = 0;
        int ok_signals = 0;
        int done_signals = 0;
        int skipped_signals = 0;
        int waiting_signals = 0;
        int timeout_signals = 0;
        int too_high_price_at_start = 0;
        int stoploss_signals = 0;
        int stoploss_done_signals = 0;
        int price1_not_set = 0;
        int price2_not_set = 0;
        int price_target_not_set = 0;
        int copy_signals = 0;
        long done_millis_diff_summ = 0;
        long copy_millis_diff_summ = 0;
        double done_summ_percent = 0;
        double summ_percent = 0;
        double summ_rating = 0;
        Set<Integer> used_regexps = new HashSet<>();
        Set<String> copy_sources = new HashSet<>();
    }
    private final HashMap<String, ChannelStat> channel_stats = new HashMap<>();
    private final Set<Integer> channel_used_regexps = new HashSet<>();
    
    private final int MAX_DAYS = 6;
    private final int SKIP_DAYS = 30;
    private int STOPLOSS_PERCENT = 10;
    private final int NO_SIGNAL_MINUTES_TO_RESTART = 360;

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
    private static final DecimalFormat df8 = new DecimalFormat("0.#######");

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
        if (mainApplication.getInstance().getProfitsChecker() != null && mainApplication.getInstance().getProfitsChecker().getStopLossPercent() != null) {
            STOPLOSS_PERCENT = mainApplication.getInstance().getProfitsChecker().getStopLossPercent().intValue();
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
                    logStats();
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
    
    private void logStats() {
        checkSignalItems();
        mainApplication.getInstance().log("");
        mainApplication.getInstance().log("Channel stats:\n");
        for (Map.Entry<String, ChannelStat> entry : channel_stats.entrySet()) {
            if (!entry.getKey().isEmpty() && !entry.getKey().equals("-")) {
                long avg_time = 0;
                double avg_done_percent = 0;
                double avg_percent = 0;
                double avg_rating = 0;
                double avg_copy_diff = 0;
                if (entry.getValue().done_signals > 0) {
                    avg_time = entry.getValue().done_millis_diff_summ / entry.getValue().done_signals;
                    avg_done_percent = entry.getValue().done_summ_percent / entry.getValue().done_signals;
                }
                if (entry.getValue().ok_signals > 0) {
                    avg_percent = entry.getValue().summ_percent / entry.getValue().ok_signals;
                    avg_rating = entry.getValue().summ_rating / entry.getValue().ok_signals;
                }
                if (entry.getValue().copy_signals > 0) {
                    avg_copy_diff = entry.getValue().copy_millis_diff_summ / entry.getValue().copy_signals;
                }
                mainApplication.getInstance().log(
                    "Channel '" + entry.getKey() + "' Stats:\n" +
                    "Signals count: " + entry.getValue().signals_cnt + "\n" +
                    "Normal signals: " + entry.getValue().ok_signals + "\n" +
                    "Signals with price > price2 at start: " + entry.getValue().too_high_price_at_start + "\n" +
                    "Timeout signals ("+MAX_DAYS+"d): " + entry.getValue().timeout_signals + "\n" +
                    "Skipped signals (default: "+SKIP_DAYS+"d): " + entry.getValue().skipped_signals + "\n" +
                    "Waiting signals: " + entry.getValue().waiting_signals + "\n" + 
                    "Stoploss signals (default: -"+STOPLOSS_PERCENT+"%): " + entry.getValue().stoploss_signals + "\n" +
                    "Stoploss then done signals: " + entry.getValue().stoploss_done_signals + "\n" + 
                    "Signals without price1: " + entry.getValue().price1_not_set + "\n" + 
                    "Signals without price2: " + entry.getValue().price2_not_set + "\n" + 
                    "Signals without target: " + entry.getValue().price_target_not_set + "\n" + 
                    "Channel avg rating: " + df8.format(avg_rating) + "\n" + 
                    "Done signals: " + entry.getValue().done_signals + "\n" + 
                    "Done signals avg time: " + df8.format(avg_time / 86400000.0) + " day.\n" + 
                    "Done signals avg percent profit: " + df8.format(avg_done_percent) + "%\n" + 
                    "Channel avg signal profit: " + df8.format(avg_percent) + "%\n" + 
                    "Copy signals: " + entry.getValue().copy_signals + "\n" + 
                    "Channel copy sources: " + entry.getValue().copy_sources + "\n" + 
                    "Channel copy avg interval: " + df8.format(avg_copy_diff / 60000) + " min.\n" + 
                    "Channel regexp_ids: " + entry.getValue().used_regexps + "\n" + 
                    "\n"
                );
            }
        }
        mainApplication.getInstance().log("Set of all channels regexp_ids: " + channel_used_regexps.toString());
        mainApplication.getInstance().log("");
    }
    
    private Double alignPrice(Double price, Double current) {
        while (Math.abs(price - current) / current > 5) {
            price *= 0.1;
        }
        return price;
    }
    
    private double getPriceForDateTime(List<Bar> bars, LocalDateTime datetime) {
        double result = bars.get(0).getClosePrice().doubleValue();
        for(int i = 0; i < bars.size(); i++) {
            if (
                    (
                        bars.get(i).getBeginTime().toLocalDateTime().isBefore(datetime) ||
                        bars.get(i).getBeginTime().toLocalDateTime().isEqual(datetime)
                    ) && 
                    bars.get(i).getEndTime().toLocalDateTime().isAfter(datetime)
            ) {
                result = bars.get(i).getClosePrice().doubleValue();
                break;
            }
        }
        return result;
    }
    
    private void addSignal(String symbol1, String symbol2, LocalDateTime datetime, double rating, Double price1, Double price2, Double price_target, Double price_stoploss, int exp_id, String title) {
        if (title != null && !title.isEmpty()) {
            ChannelStat stat;
            if (!channel_stats.containsKey(title)) {
                stat = new ChannelStat();
                channel_stats.put(title, stat);
            } else {
                stat = channel_stats.get(title);
            }
            stat.signals_cnt++;
            stat.used_regexps.add(exp_id);
            
            if (price1 == null) stat.price1_not_set++;
            if (price2 == null) stat.price2_not_set++;
            if (price_target == null) stat.price_target_not_set++;
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
        List<Bar> bars = null;
        try {
            bars = client.getBars(symbol1 + symbol2, "15m");
        } catch(Exception e) {}
        if (bars == null || bars.isEmpty()) {
            return;
        }

        boolean price1_a = false;
        boolean price2_a = false;
        boolean priceT_a = false;
        boolean priceS_a = false;

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
            price1 = getPriceForDateTime(bars, datetime) * 0.95;
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

        boolean is_skipped = LocalDateTime.now().minusDays(SKIP_DAYS).isAfter(datetime) || datetime.isBefore(bars.get(0).getBeginTime().toLocalDateTime());
        if (is_skipped && channel_stats.containsKey(title)) {
            ChannelStat stat = channel_stats.get(title);
            stat.skipped_signals++;
            return;
        }

        double enter_price = getPriceForDateTime(bars, datetime);
        if (enter_price <= 0) enter_price = 0.5 * (price1 + price2);
        boolean is_timeout = LocalDateTime.now().minusDays(MAX_DAYS).isAfter(datetime);
        
        ZoneOffset localOffset = OffsetDateTime.now().getOffset();
        
        double max_order_price = (price2*90+price_target*10)/100;
        
        boolean is_done = false;
        boolean is_stop_loss = false;
        boolean is_price_too_high = enter_price >= max_order_price;
        long millis_signal = datetime.toInstant(localOffset).toEpochMilli();
        long millis_done = 0;
        
        /*System.out.println("signal "+symbol1+" datetime = " + datetime);
        System.out.println("millis_signal = " + millis_begin + " === " + LocalDateTime.ofEpochSecond(millis_begin / 1000, 0, ZoneOffset.UTC));
        System.out.println("millis_lastbar_begin = " + bars.get(bars.size()-1).getBeginTime().toInstant().toEpochMilli() + " === " + LocalDateTime.ofEpochSecond(bars.get(bars.size()-1).getBeginTime().toInstant().toEpochMilli() / 1000, 0, ZoneOffset.UTC));
        System.out.println("millis_lastbar_end = " + bars.get(bars.size()-1).getEndTime().toInstant().toEpochMilli() + " === " + LocalDateTime.ofEpochSecond(bars.get(bars.size()-1).getEndTime().toInstant().toEpochMilli() / 1000, 0, ZoneOffset.UTC));
        System.out.println("millis_now = " + System.currentTimeMillis() + " === " + LocalDateTime.ofEpochSecond(System.currentTimeMillis() / 1000, 0, ZoneOffset.UTC));
        System.out.println("");*/

        double done_percent = 0;
        int signal_fast_index = 0;
        int signal_normal_index = 0;
        for(int i=0; i<bars.size() && !is_done; i++) {
            Bar bar = bars.get(i);
            long bar_begin_millis = bar.getBeginTime().toInstant().toEpochMilli();
            long bar_end_millis = bar.getEndTime().toInstant().toEpochMilli();
            if (
                bar_begin_millis <= millis_signal && 
                bar_end_millis >= millis_signal
            ) {
                if (signal_fast_index == 0) signal_fast_index = i;
                if (signal_normal_index == 0) {
                    if (((bar.getMinPrice().doubleValue() + bar.getClosePrice().doubleValue() + bar.getMaxPrice().doubleValue()) / 3) < max_order_price) {
                        signal_normal_index = i;
                    }
                }
            }
            if (signal_fast_index > 0) {
                is_done = bar.getMaxPrice().doubleValue() >= price_target;
                if (is_done) {
                    done_percent = 100 * (bar.getMaxPrice().doubleValue() - enter_price) / enter_price;
                    millis_done = (bar_end_millis + bar_begin_millis) / 2;
                    if (millis_done < millis_signal) millis_done = bar_end_millis;
                    break;
                }
                if (bar.getMinPrice().doubleValue() <= price_stoploss) {
                    is_stop_loss = true;
                }
            }
        }
        if (is_done) is_timeout = false;
        
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
        s.setDone(is_done || is_timeout);
        s.setTimeout(is_timeout);
        s.setChannelName(title);
        s.updateHash();
        items.add(s);
        
        if (channel_stats.containsKey(title)) {
            ChannelStat stat = channel_stats.get(title);
            stat.ok_signals++;
            if (is_done) stat.done_signals++; 
            else if (!is_timeout) stat.waiting_signals++;
            else if (is_timeout) stat.timeout_signals++;
            if (is_done) {
                stat.done_millis_diff_summ += millis_done - millis_signal;
                stat.done_summ_percent += done_percent;
            }
            if (is_stop_loss) stat.stoploss_signals++;
            if (is_done && is_stop_loss) stat.stoploss_done_signals++;
            
            if (is_stop_loss) {
                stat.summ_percent -= STOPLOSS_PERCENT;
            } else if (is_done) {
                stat.summ_percent += done_percent;
            } else {
                stat.summ_percent += 100 * (currPrice - enter_price) / enter_price;
            }
            if (is_price_too_high) {
                stat.too_high_price_at_start++;
            }
            stat.summ_rating += rating;
        }

        if (initialSignalsIsLoaded) {
            checkSignalItems();
        }

        boolean is_copy = !items.contains(s);
        
        mainApplication.getInstance().log(
            "Signal "
            + symbol1 + symbol2 + " from " 
            + datetime.format(df) + " (channel = '"+title+"') " 
            + "rating = " + rating + "; "
            + df8.format(price1) + " - " 
            + df8.format(price2) + " ===> " 
            + df8.format(price_target)
            + "; Stoploss: " + df8.format(price_stoploss) + " : "
            + (is_done ? "DONE" : (is_timeout ? "TIMEOUT" : "WAITING")) + " "
            + (is_copy ? "COPY" : "")
        );
        
        if (signalEvent != null && !is_copy) {
            signalEvent.onUpdate(s, getPairSignalRating(s.getPair()));
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
        for(int i=0; i< items.size(); i++) {
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
}
