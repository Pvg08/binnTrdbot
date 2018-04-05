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
import java.util.LinkedList;
import java.util.List;
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

    private final int MAX_DAYS = 4;

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
        if (str == null || str.isEmpty()) {
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
            }
            return;
        }
        String[] parts = line.split(";");
        if (parts.length != 8) {
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
                strPrice(parts[6])  // price_target
            );
        } else {
            mainApplication.getInstance().log(line);
        }
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
    
    private void addSignal(String symbol1, String symbol2, LocalDateTime datetime, double rating, Double price1, Double price2, Double price_target) {
        if (symbol1 == null || symbol1.isEmpty() || datetime == null || rating == 0 || price_target == null) {
            return;
        }
        if (price1 == null && price2 != null) {
            price1 = price2;
            price2 = null;
        }
        if (price1 != null) {
            if (price2 != null && price2 < price1) {
                return;
            }
            if (price2 == null) {
                price2 = price1;
                price1 = price1 * 0.95;
            }
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
        Double currPrice = bars.get(bars.size()-1).getClosePrice().doubleValue();
        if (price1 != null) {
            price1 = alignPrice(price1, currPrice);
        }
        if (price2 != null) {
            price2 = alignPrice(price2, currPrice);
        }
        price_target = alignPrice(price_target, currPrice);
        if (price1 == null) {
            price1 = getPriceForDateTime(bars, datetime);
            if (price1 > price_target) {
                price1 = price_target * 0.95;
            }
            if (price2 == null) {
                price2 = price1;
                price1 = price1 * 0.95;
            }
        }

        boolean is_timeout = LocalDateTime.now().minusDays(MAX_DAYS).isAfter(datetime);
        boolean is_done = price1 > price_target;
        for(int i=0; i<bars.size() && !is_done; i++) {
            if (bars.get(i).getEndTime().toInstant().toEpochMilli() > datetime.toInstant(ZoneOffset.UTC).toEpochMilli()) {
                is_done = bars.get(i).getMaxPrice().doubleValue() > price_target;
            }
        }
        
        mainApplication.getInstance().log(
                "Signal "
                + symbol1 + symbol2 + " from " 
                + datetime.format(df) + " " 
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
