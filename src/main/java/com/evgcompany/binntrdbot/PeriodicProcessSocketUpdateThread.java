/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author EVG_adm_T
 */
abstract public class PeriodicProcessSocketUpdateThread extends PeriodicProcessThread {
    
    private Closeable socket = null;
    private boolean socket_closed = false;
    
    public PeriodicProcessSocketUpdateThread() {
        super();
    }
    public PeriodicProcessSocketUpdateThread(String threadName) {
        super(threadName);
    }
    
    @Override
    public void finalize() throws Throwable {
        if (socket != null) {
            socket.close();
        }
        super.finalize();
    }
    
    protected void stopSocket() {
        if (socket != null) {
            try {
                mainApplication.getInstance().log("Stopping socket for thread "+threadName+"...");
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(TradePairProcess.class.getName()).log(Level.SEVERE, null, ex);
            }
            socket = null;
        }
    }
    
    protected void socketClosed(String reason) {
        mainApplication.getInstance().log("Socket failure in thread "+threadName+": " + reason);
        socket_closed = true;
    }
    
    @Override
    protected void preBody() {
        if (socket_closed) {
            mainApplication.getInstance().log("Restarting socket in thread "+threadName+"...");
            socket_closed = false;
            stopSocket();
            doWait(500);
            initSocket();
        }
    }
    
    @Override
    protected void postFinish() {
        stopSocket();
    }

    @Override
    protected void postInit() {
        initSocket();
    }
    
    abstract protected void initSocket();
    
    protected void setSocket(Closeable socket) {
        stopSocket();
        mainApplication.getInstance().log("Starting socket for thread "+threadName+"...");
        socket_closed = false;
        this.socket = socket;
    }
}
