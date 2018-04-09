/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.ta4j.core.Decimal;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyConfig {
    private final HashMap<String, StrategyConfigItem> params = new HashMap<>();
    private final List<String> param_names = new ArrayList<>();
    private static final DecimalFormat df6 = new DecimalFormat("0.######");
    
    public StrategyConfigItem Add(String name, StrategyConfigItem item) {
        params.put(name, item);
        param_names.add(name);
        return item;
    }
    
    public StrategyConfigItem GetItem(String name) {
        return params.get(name);
    }
    
    public HashMap<String, StrategyConfigItem> getItems() {
        return params;
    }
    
    public BigDecimal GetValue(String name) {
        return params.get(name).getValue();
    }

    public double GetDoubleValue(String name) {
        return params.get(name).getValue().doubleValue();
    }

    public long GetLongValue(String name) {
        return params.get(name).getValue().longValue();
    }
    public int GetIntValue(String name) {
        return params.get(name).getValue().intValue();
    }
    public Decimal GetNumValue(String name) {
        return Decimal.valueOf(params.get(name).getValue());
    }
    
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        param_names.forEach((pname) -> {
            StrategyConfigItem item = params.get(pname);
            if (item != null && item.isActive()) {
                parts.add(df6.format(item.getValue()).replaceAll(",", "."));
            }
        });
        if (!parts.isEmpty())
            return " " + Arrays.toString(parts.toArray()).replaceAll(" ", "");
        else 
            return "";
    }
}
