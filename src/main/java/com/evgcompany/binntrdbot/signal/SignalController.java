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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
        int waiting_signals = 0;
        int timeout_signals = 0;
        int stoploss_signals = 0;
        int stoploss_done_signals = 0;
        long done_millis_diff_summ = 0;
        double done_summ_percent = 0;
        double summ_percent = 0;
    }
    private final HashMap<String, ChannelStat> channel_stats = new HashMap<>();
    
    private final int MAX_DAYS = 6;
    private final int STOPLOSS_PERCENT = 10;

    private TradingAPIAbstractInterface client = null;
    private String signalsListenerFile;
    private String apiId = "";
    private String apiHash = "";
    private String apiPhone = "";
    private StartedProcess signalsProcess = null;
    private boolean isChecking = false;
    private final LinkedList<String> newlines = new LinkedList<>();
    private final List<SignalItem> items = new ArrayList<>(0);
    
    private SignalEvent signalEvent = null;
    private boolean initialSignalsIsLoaded = false;

    private static final DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat df8 = new DecimalFormat("0.#######");

    public SignalController() {
        signalsListenerFile = "TGsignalsListener.py";
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (signalsProcess != null && signalsProcess.getProcess().isAlive()) {
            signalsProcess.getProcess().destroy();
            signalsProcess = null;
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
        initialSignalsIsLoaded = false;
        while (signalsProcess != null && signalsProcess.getProcess().isAlive()) {
            doWait(500);
            if (!newlines.isEmpty()) {
                onSignalNewLine(newlines.removeFirst());
            }
        }
        if (signalsProcess != null) {
            stopSignalsProcess();
        }
        initialSignalsIsLoaded = true;
    }
    
    public void startSignalsProcess(boolean isChecking) {
        try {
            initialSignalsIsLoaded = false;
            mainApplication.getInstance().log("Starting process: " + signalsListenerFile);
            this.isChecking = isChecking;
            signalsProcess = new ProcessExecutor()
                .command("python", "-u", signalsListenerFile, apiId, apiHash, apiPhone)
                .redirectOutput(new LogOutputStream() {
                    @Override
                    protected void processLine(String line) {
                        System.out.println(line);
                        newlines.addLast(line);
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
            start();
        } catch (IOException | InvalidExitValueException ex) {
            mainApplication.getInstance().log(ex.getMessage());
            initialSignalsIsLoaded = true;
        }
    }
    public void startSignalsProcess() {
        startSignalsProcess(false);
    }
    
    public void stopSignalsProcess() {
        signalsProcess.getProcess().destroy();
        signalsProcess = null;
        initialSignalsIsLoaded = true;
        mainApplication.getInstance().log("Stopping process: " + signalsListenerFile);
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
        if (line.startsWith("------------------")) {
            initialSignalsIsLoaded = true;
            if (isChecking) {
                stopSignalsProcess();
            } else {
                logStats();
            }
            return;
        }
        String[] parts = line.split(";");
        if (parts.length != 9) {
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
                parts[8] // channel title
            );
        } else {
            mainApplication.getInstance().log(line);
        }
    }
    
    private void logStats() {
        for (Map.Entry<String, ChannelStat> entry : channel_stats.entrySet()) {
            long avg_time = 0;
            double avg_done_percent = 0;
            double avg_percent = 0;
            if (entry.getValue().done_signals > 0) {
                avg_time = entry.getValue().done_millis_diff_summ / entry.getValue().done_signals;
                avg_done_percent = entry.getValue().done_summ_percent / entry.getValue().done_signals;
            }
            if (entry.getValue().ok_signals > 0) {
                avg_percent = entry.getValue().summ_percent / entry.getValue().ok_signals;
            }
            mainApplication.getInstance().log(
                "Channel '" + entry.getKey() + "' Stats:\n" +
                "Signals count: " + entry.getValue().signals_cnt + "\n" +
                "Normal signals: " + entry.getValue().ok_signals + "\n" +
                "Timeout signals ("+MAX_DAYS+"d): " + entry.getValue().timeout_signals + "\n" +
                "Waiting signals: " + entry.getValue().waiting_signals + "\n" + 
                "Stoploss signals (-"+STOPLOSS_PERCENT+"%): " + entry.getValue().stoploss_signals + "\n" +
                "Stoploss then done signals: " + entry.getValue().stoploss_done_signals + "\n" + 
                "Done signals: " + entry.getValue().done_signals + "\n" + 
                "Done signals avg time: " + df8.format(avg_time / 86400000.0) + " day.\n" + 
                "Done signals avg percent profit: " + df8.format(avg_done_percent) + "%\n" + 
                "Channel avg signal profit: " + df8.format(avg_percent) + "%\n" + 
                "\n"
            );
        }
        mainApplication.getInstance().log("");
    }
    
    private Double alignPrice(Double price, Double current) {
        while (Math.abs(price - current) / current > 5) {
            price *= 0.1;
        }
        return price;
    }
    
    private double getPriceForDateTime(List<Bar> bars, LocalDateTime datetime) {
        double result = bars.get(bars.size()-1).getClosePrice().doubleValue();
        for(int i = 0; i < bars.size(); i++) {
            if (bars.get(i).getBeginTime().toLocalDateTime().isBefore(datetime) && bars.get(i).getEndTime().toLocalDateTime().isAfter(datetime)) {
                result = bars.get(i).getClosePrice().doubleValue();
            }
        }
        return result;
    }
    
    private void addSignal(String symbol1, String symbol2, LocalDateTime datetime, double rating, Double price1, Double price2, Double price_target, String title) {
        if (title != null && !title.isEmpty()) {
            ChannelStat stat;
            if (!channel_stats.containsKey(title)) {
                stat = new ChannelStat();
                channel_stats.put(title, stat);
            } else {
                stat = channel_stats.get(title);
            }
            stat.signals_cnt++;
        }
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

        if (price1 == null) {
            price1 = getPriceForDateTime(bars, datetime) * 0.975;
        }
        if (price2 == null) {
            price2 = price1 * 1.025;
        }
        if (price_target == null) {
            price_target = price2 * 1.05;
        }

        Double currPrice = bars.get(bars.size()-1).getClosePrice().doubleValue();
        price1 = alignPrice(price1, currPrice);
        price2 = alignPrice(price2, currPrice);
        price_target = alignPrice(price_target, currPrice);

        if (price1 > price_target) {
            price1 = price_target * 0.95;
        }
        if (price2 > price_target) {
            price2 = price_target * 0.99;
        }
        if (price1 > price2) {
            double tmp = price1;
            price1 = price2;
            price2 = tmp;
        }

        double enter_price = getPriceForDateTime(bars, datetime);
        if (enter_price <= 0) enter_price = price1;
        
        double stop_loss = enter_price * (100-STOPLOSS_PERCENT) / 100;
        boolean is_timeout = LocalDateTime.now().minusDays(MAX_DAYS).isAfter(datetime);
        boolean is_done = false;
        boolean is_stop_loss = false;
        long millis_begin = datetime.toInstant(ZoneOffset.UTC).toEpochMilli();
        long millis_done = 0;
        double done_percent = 0;
        for(int i=0; i<bars.size() && !is_done; i++) {
            if (bars.get(i).getEndTime().toInstant().toEpochMilli() > millis_begin) {
                is_done = bars.get(i).getMaxPrice().doubleValue() >= price_target;
                if (is_done) {
                    done_percent = 100 * (bars.get(i).getMaxPrice().doubleValue() - enter_price) / enter_price;
                    millis_done = bars.get(i).getEndTime().toInstant().toEpochMilli();
                    break;
                }
                if (bars.get(i).getMinPrice().doubleValue() < stop_loss) {
                    is_stop_loss = true;
                }
            }
        }
        if (is_done) is_timeout = false;

        if (channel_stats.containsKey(title)) {
            ChannelStat stat = channel_stats.get(title);
            stat.ok_signals++;
            if (is_done) stat.done_signals++; 
            else if (!is_timeout) stat.waiting_signals++;
            else if (is_timeout) stat.timeout_signals++;
            if (is_done) {
                stat.done_millis_diff_summ += millis_done - millis_begin;
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
        }

        mainApplication.getInstance().log(
            "Signal "
            + symbol1 + symbol2 + " from " 
            + datetime.format(df) + " (channel = '"+title+"') " 
            + "rating = " + rating + "; "
            + df8.format(price1) + " - " 
            + df8.format(price2) + " ===> " 
            + df8.format(price_target) + " "
            + (is_done ? "DONE" : (is_timeout ? "TIMEOUT" : "WAITING"))
        );

        SignalItem s = new SignalItem();
        s.setDatetime(datetime);
        s.setSymbol1(symbol1);
        s.setSymbol2(symbol2);
        s.setPriceFrom(new BigDecimal(price1));
        s.setPriceTo(new BigDecimal(price2));
        s.setPriceTarget(new BigDecimal(price_target));
        s.setRating(rating);
        s.setDone(is_done || is_timeout);
        s.setTimeout(is_timeout);
        s.setChannelName(title);
        items.add(s);
        
        if (signalEvent != null) {
            signalEvent.onUpdate(s, getPairSignalRating(s.getPair()));
        }
    }

    public double getPairSignalRating(String symbol) {
        double result = 0;
        for(int i=0; i< items.size(); i++) {
            if (items.get(i).getPair().equals(symbol) || items.get(i).getSymbol1().equals(symbol)) {
                if (!items.get(i).isTimeout() && LocalDateTime.now().minusDays(MAX_DAYS).isAfter(items.get(i).getDatetime())) {
                    items.get(i).setTimeout(true);
                    items.get(i).setDone(true);
                }
                double millis_maxago = LocalDateTime.now().minusDays(MAX_DAYS).toInstant(ZoneOffset.UTC).toEpochMilli();
                double millis_dtime = items.get(i).getDatetime().toInstant(ZoneOffset.UTC).toEpochMilli();
                double millis_now = System.currentTimeMillis();
                double koef = 0;
                double rating = 0;
                if (!items.get(i).isTimeout()) {
                    if (!items.get(i).isDone()) {
                        rating = items.get(i).getRating() + 0.5;
                        koef = 1.0 - (millis_now - millis_dtime) / (millis_now - millis_maxago);
                        if (koef > 1) {
                            koef = 1;
                        } else if (koef < 0.1) {
                            koef = 0.1;
                        }
                    } else {
                        rating = items.get(i).getRating() / 2.5;
                        koef = 0.1;
                    }
                } else {
                    rating = 0.5;
                    koef = 0.1;
                }
                result = result + rating * koef;
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
}
