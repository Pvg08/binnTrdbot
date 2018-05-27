/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

/**
 *
 * @author EVG_adm_T
 */
abstract public class RunProcessNoThread {

    protected boolean isInitialized = false;
    protected boolean stopped = false;
    protected String threadName;

    public RunProcessNoThread(String threadName) {
        this.threadName = threadName;
    }
    public RunProcessNoThread() {
        this.threadName = this.getClass().toString();
    }

    public void doStop() {
        stopped = true;
        runFinish();
        postFinish();
    }

    /**
     * @return the isInitialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    abstract protected void runStart();
    abstract protected void runFinish();
    
    public void start(){  
        run();
    }
    
    public void run(){
        stopped = false;
        isInitialized = false;
        preStart();
        runStart();
        isInitialized = true;
        postInit();
    }

    protected void preStart() {
        // nothing
    }
    protected void postInit() {
        // nothing
    }
    protected void postFinish() {
        // nothing
    }

    /**
     * @return the need_stop
     */
    public boolean isStop() {
        return stopped;
    }

    public boolean isAlive() {
        return !stopped;
    }
}
