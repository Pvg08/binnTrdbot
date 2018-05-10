/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author EVG_adm_T
 */
abstract public class PeriodicProcessThread extends Thread {

    protected final Semaphore SEMAPHORE_STARTWAIT = new Semaphore(1, true);
    
    protected boolean need_stop = false;
    protected boolean paused = false;
    protected long delayTime = 10;
    protected int startDelayTime = 0;
    protected int maxExceptionsToStop = 4;
    protected boolean isInitialized = false;

    protected void doWait(long ms) {
        try { Thread.sleep(ms);} catch(InterruptedException e) {}
    }

    public void doStop() {
        need_stop = true;
        paused = false;
    }

    public void doSetPaused(boolean _paused) {
        paused = _paused;
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

    public void setStartDelay(int startDelayTime) {
        this.startDelayTime = startDelayTime;
    }

    public void StartAndWaitForInit() {
        try {
            SEMAPHORE_STARTWAIT.acquire();
            need_stop = false;
            boolean starting = false;
            if (!isAlive()) {
                starting = true;
                start();
                while (!isAlive()) {
                    doWait(1000);
                }
                doWait(100);
            }
            waitForInit();
            if (starting) {
                doWait(500);
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(PeriodicProcessThread.class.getName()).log(Level.SEVERE, null, ex);
        }
        SEMAPHORE_STARTWAIT.release();
    }
    
    protected void waitForInit() {
        while (!isInitialized) {
            doWait(500);
        }
    }
    
    /**
     * @return the isInitialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    abstract protected void runStart();
    abstract protected void runBody();
    abstract protected void runFinish();
    
    @Override
    public void run(){  
        isInitialized = false;
        int exceptions_cnt = 0;
        doWait(startDelayTime);
        runStart();
        isInitialized = true;
        while (!need_stop) {
            if (paused) {
                doWait(12000);
                continue;
            }
            try { 
                runBody();
            } catch(Exception exx) {
                exx.printStackTrace(System.out);
                mainApplication.getInstance().log("");
                mainApplication.getInstance().log("EXCEPTION in " + getClass() + " - " + exx.getClass() + ": " + exx.getLocalizedMessage(), false, true);
                exceptions_cnt++;
                if (exceptions_cnt >= maxExceptionsToStop) {
                    break;
                }
                doWait(delayTime * 1000 * exceptions_cnt * exceptions_cnt);
                continue;
            }
            doWait(delayTime * 1000);
            exceptions_cnt = 0;
        }
        runFinish();
    }

    /**
     * @return the need_stop
     */
    public boolean isStop() {
        return !isAlive() || need_stop;
    }
}
