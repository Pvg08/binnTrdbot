/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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

    private TradingAPIAbstractInterface client = null;
    private String signalsListenerFile;
    private String apiId = "";
    private String apiHash = "";
    private String apiPhone = "";
    private StartedProcess signalsProcess = null;
    private boolean isChecking = false;
    private final LinkedList<String> newlines = new LinkedList<>();
    
    private static final Semaphore SEMAPHORE_ADD = new Semaphore(1, true);
    private DateTimeFormatter df = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat df8 = new DecimalFormat("0.#######");

    public SignalController() {
        signalsListenerFile = "TGsignalsListener.py";
    }
    
    private void doWait(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }
    
    @Override
    public void run() {
        while (signalsProcess != null && signalsProcess.getProcess().isAlive()) {
            doWait(500);
            if (!newlines.isEmpty()) {
                onSignalNewLine(newlines.removeFirst());
            }
        }
        if (signalsProcess != null) {
            stopSignalsProcess();
        }
    }
    
    public void startSignalsProcess(boolean isChecking) {
        try {
            mainApplication.getInstance().log("Starting process: " + signalsListenerFile);
            this.isChecking = isChecking;
            signalsProcess = new ProcessExecutor()
                .command("python", "-u", signalsListenerFile, apiId, apiHash, apiPhone)
                .redirectOutput(new LogOutputStream() {
                    @Override
                    protected void processLine(String line) {
                        newlines.addLast(line);
                    }
                })
                .redirectError(new LogOutputStream() {
                    @Override
                    protected void processLine(String line) {
                        mainApplication.getInstance().log("Error: " + line);
                    }
                })
                .redirectInput(System.in)
                .start();
            start();
        } catch (IOException | InvalidExitValueException ex) {
            mainApplication.getInstance().log(ex.getMessage());
        }
    }
    public void startSignalsProcess() {
        startSignalsProcess(false);
    }
    
    public void stopSignalsProcess() {
        signalsProcess.getProcess().destroy();
        signalsProcess = null;
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
        if (isChecking && line.startsWith("------------------")) {
            stopSignalsProcess();
            return;
        }
        String[] parts = line.split(";");
        if (parts.length != 7) {
            return;
        }
        if (!isChecking) {
            addSignal(
                parts[2], // symbol1
                parts[3], // symbol2
                df.parseLocalDateTime(parts[1]), // datetime
                Integer.parseInt(parts[0]), // rating
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
    
    private void addSignal(String symbol1, String symbol2, LocalDateTime datetime, int rating, Double price1, Double price2, Double price_target) {
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
            if (price1 > price_target) {
                return;
            }
            if (price2 == null) {
                price2 = price1;
                price1 = price1 * 0.75;
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
        
        mainApplication.getInstance().log(
                "Signal "
                + symbol1 + symbol2 + " from " 
                + datetime + " " 
                + "rating = " + rating + "; "
                + (price1 != null ? df8.format(price1) : "N") + " - " 
                + (price2 != null ? df8.format(price2) : "N") + " ===> " 
                + df8.format(price_target)
        );
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
}
