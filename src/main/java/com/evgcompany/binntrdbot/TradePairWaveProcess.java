/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.signal.SignalItem;
import java.math.BigDecimal;

/**
 *
 * @author EVG_Adminer
 */
public class TradePairWaveProcess extends AbstractTradePairProcess {
    
    private double wavesPrimaryBuy = 10;
    private boolean wavesPrimaryBasePercent = true;
    private double wavesSecondaryPercent = 25;
    private double wavesIncKoef = 59;
    
    private BigDecimal lastPrice = BigDecimal.ZERO;
    private BigDecimal minPrice = BigDecimal.ZERO;
    private BigDecimal aimPrice = BigDecimal.ZERO;
    
    private SignalItem signalItem = null;
    
    public TradePairWaveProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
        pyramidAutoMaxSize = Integer.MAX_VALUE;
        longModeAuto = true;
    }

    @Override
    protected void checkStatus() {
        // doEnter(lastStrategyCheckPrice);
        // doExit(lastStrategyCheckPrice, false);
        super.checkStatus();
        
    }
    
    /**
     * @return the wavesPrimaryBuy
     */
    public double getWavesPrimaryBuy() {
        return wavesPrimaryBuy;
    }

    /**
     * @param wavesPrimaryBuy the wavesPrimaryBuy to set
     */
    public void setWavesPrimaryBuy(double wavesPrimaryBuy) {
        this.wavesPrimaryBuy = wavesPrimaryBuy;
    }

    /**
     * @return the wavesSecondaryPercent
     */
    public double getWavesSecondaryPercent() {
        return wavesSecondaryPercent;
    }

    /**
     * @param wavesSecondaryPercent the wavesSecondaryPercent to set
     */
    public void setWavesSecondaryPercent(double wavesSecondaryPercent) {
        this.wavesSecondaryPercent = wavesSecondaryPercent;
    }

    /**
     * @return the wavesIncKoef
     */
    public double getWavesIncKoef() {
        return wavesIncKoef;
    }

    /**
     * @param wavesIncKoef the wavesIncKoef to set
     */
    public void setWavesIncKoef(double wavesIncKoef) {
        this.wavesIncKoef = wavesIncKoef;
    }

    /**
     * @return the wavesPrimaryBasePercent
     */
    public boolean isWavesPrimaryBasePercent() {
        return wavesPrimaryBasePercent;
    }

    /**
     * @param wavesPrimaryBasePercent the wavesPrimaryBasePercent to set
     */
    public void setWavesPrimaryBasePercent(boolean wavesPrimaryBasePercent) {
        this.wavesPrimaryBasePercent = wavesPrimaryBasePercent;
    }
}
