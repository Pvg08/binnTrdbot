/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.misc;

import java.text.DecimalFormat;

/**
 *
 * @author EVG_Adminer
 */
public class NumberFormatter {
    public static final DecimalFormat df1 = new DecimalFormat("0.#");
    public static final DecimalFormat df2 = new DecimalFormat("0.##");
    public static final DecimalFormat df3 = new DecimalFormat("0.###");
    public static final DecimalFormat df4 = new DecimalFormat("0.####");
    public static final DecimalFormat df5 = new DecimalFormat("0.#####");
    public static final DecimalFormat df6 = new DecimalFormat("0.######");
    public static final DecimalFormat df7 = new DecimalFormat("0.#######");
    public static final DecimalFormat df8 = new DecimalFormat("0.########");
    
    public static final DecimalFormat df1p = new DecimalFormat("0.#%");
    public static final DecimalFormat df2p = new DecimalFormat("0.##%");
    public static final DecimalFormat df3p = new DecimalFormat("0.###%");
    
    public static String formatVolume(double volume) {
        String result = "";
        int pc = 0;
        while (volume > 1000) {
            volume = volume * 0.001;
            pc++;
        }
        result = NumberFormatter.df3.format(volume);
        switch (pc) {
            case 1:
                result += " k";
                break;
            case 2:
                result += " M";
                break;
            case 3:
                result += " G";
                break;
            case 4:
                result += " T";
                break;
            case 5:
                result += " P";
                break;
            case 6:
                result += " E";
                break;
            case 7:
                result += " Z";
                break;
            case 8:
                result += " Y";
                break;
            default:
                if (pc > 0) result += " * 10^" + (pc*3);
                break;
        }
        return result;
    }
    
    public static String formatPercentChange(double value, double value_initial) {
        if (value_initial > 0) {
            double changePercent = (value - value_initial) / value_initial;
            return (changePercent>=0 ? "+" : "") + NumberFormatter.df3p.format(changePercent);
        }
        return "+0%";
    }

    public static String formatOffsetChange(double value, double value_initial) {
        if (value_initial > 0) {
            double change = value - value_initial;
            return (change>=0 ? "+" : "") + NumberFormatter.df3.format(change);
        }
        return "+0";
    }
}
