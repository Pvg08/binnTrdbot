/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import java.math.BigDecimal;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyConfigItem {
    private BigDecimal min;
    private BigDecimal max;
    private BigDecimal step;
    private final BigDecimal default_value;
    private BigDecimal value;
    private boolean active;

    public StrategyConfigItem(BigDecimal min, BigDecimal max, BigDecimal step, BigDecimal default_value) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.default_value = default_value;
        this.value = this.default_value;
        this.active = true;
    }
    public StrategyConfigItem(String min, String max, String step, String default_value) {
        this.min = new BigDecimal(min);
        this.max = new BigDecimal(max);
        this.step = new BigDecimal(step);
        this.default_value = new BigDecimal(default_value);
        this.value = this.default_value;
        this.active = true;
    }
    
    public void scanStart() {
        value = min;
    }

    public boolean scanNext() {
        value = value.add(step);
        if (value.compareTo(max) > 0) {
            value = max;
            return false;
        }
        return true;
    }

    public boolean scanFinished() {
        if (value.compareTo(max) < 0) {
            return false;
        }
        return true;
    }
    
    public void resetValue() {
        value = default_value;
    }

    /**
     * @return the min
     */
    public BigDecimal getMin() {
        return min;
    }

    /**
     * @param min the min to set
     */
    public void setMin(BigDecimal min) {
        this.min = min;
    }

    /**
     * @return the max
     */
    public BigDecimal getMax() {
        return max;
    }

    /**
     * @param max the max to set
     */
    public void setMax(BigDecimal max) {
        this.max = max;
    }

    /**
     * @return the step
     */
    public BigDecimal getStep() {
        return step;
    }

    /**
     * @param step the step to set
     */
    public void setStep(BigDecimal step) {
        this.step = step;
    }

    /**
     * @return the value
     */
    public BigDecimal getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(BigDecimal value) {
        this.value = value;
    }

    /**
     * @return the active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @param active the active to set
     */
    public void setActive(boolean active) {
        this.active = active;
    }
    
}
