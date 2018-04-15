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
import java.util.Map;
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
    
    public static String ConfigRowToStr(HashMap<String, BigDecimal> row) {
        String result = "";
        for (Map.Entry<String, BigDecimal> entry : row.entrySet()) {
            result += entry.getKey() + "=" + df6.format(entry.getValue()) + "; ";
        }
        return result;
    }

    private void addParamPosVariants(int pos, HashMap<String, BigDecimal> variant, List<HashMap<String, BigDecimal>> result) {
        if (pos >= 0 && pos < param_names.size()) {
            String param_name = param_names.get(pos);
            StrategyConfigItem confitem = params.get(param_name);
            if (variant == null) {
                variant = new HashMap<>();
            }
            if (!confitem.isActive()) {
                addParamPosVariants(pos + 1, variant, result);
                return;
            }
            confitem.scanStart();
            do {
                variant.put(param_name, confitem.getValue());
                addParamPosVariants(pos + 1, variant, result);
            } while(confitem.scanNext());
            confitem.resetValue();
        } else if (pos >= param_names.size() && variant.size() > 0) {
            result.add((HashMap<String, BigDecimal>) variant.clone());
            //System.out.println(ConfigRowToStr(variant));
        }
    }
    
    public List<HashMap<String, BigDecimal>> GetParamVariants() {
        List<HashMap<String, BigDecimal>> result = new ArrayList<>();
        addParamPosVariants(0, null, result);
        return result;
    }

    public void setParams(HashMap<String, BigDecimal> params) {
        for (Map.Entry<String, BigDecimal> entry : params.entrySet()) {
            if (this.params.containsKey(entry.getKey())) {
                this.params.get(entry.getKey()).setValue(entry.getValue());
            }
        }
    }

    public void setParam(String pname, BigDecimal pvalue) {
        if (this.params.containsKey(pname)) {
            this.params.get(pname).setValue(pvalue);
        }
    }

    public void resetParams() {
        for (Map.Entry<String, StrategyConfigItem> entry : params.entrySet()) {
            entry.getValue().resetValue();
        }
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
    
    public String toString(boolean with_names) {
        List<String> parts = new ArrayList<>();
        param_names.forEach((pname) -> {
            StrategyConfigItem item = params.get(pname);
            if (item != null && item.isActive()) {
                if (with_names) {
                    parts.add(pname + "=" + df6.format(item.getValue()).replaceAll(",", "."));
                } else {
                    parts.add(df6.format(item.getValue()).replaceAll(",", "."));
                }
            }
        });
        if (!parts.isEmpty())
            return " " + Arrays.toString(parts.toArray()).replaceAll(" ", "");
        else 
            return "";
    }
    
    @Override
    public String toString() {
        return toString(false);
    }
}
