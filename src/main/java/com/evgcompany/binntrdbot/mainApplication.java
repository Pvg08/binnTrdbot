/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.general.RateLimit;
import java.math.BigDecimal;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.text.DefaultCaret;

/**
 *
 * @author EVG_adm_T
 */
public class mainApplication extends javax.swing.JFrame {

    private static final Semaphore SEMAPHORE_LOG = new Semaphore(1, true);
    
    private BinanceApiClientFactory factory = null;
    private BinanceApiRestClient client = null;
    private List<tradePairProcess> pairs = new ArrayList<>(0);
    private tradeProfitsController profitsChecker = new tradeProfitsController(this);
    private HeroesController heroesController = new HeroesController(this);
    
    private boolean is_paused = false;
    Preferences prefs = null;
    
    private void componentPrefLoad(JComponent cb, String name) {
        if (cb instanceof JCheckBox) {
            ((JCheckBox)cb).setSelected("true".equals(prefs.get(name, ((JCheckBox)cb).isSelected() ? "true" : "false")));
        } else if (cb instanceof JTextField) {
            ((JTextField)cb).setText(prefs.get(name, ((JTextField)cb).getText()));
        } else if (cb instanceof JSpinner) {
            ((JSpinner)cb).setValue(Integer.parseInt(prefs.get(name, ((Integer) ((JSpinner)cb).getValue()).toString())));
        } else if (cb instanceof JComboBox) {
            ((JComboBox<String>)cb).setSelectedIndex(Integer.parseInt(prefs.get(name, ((Integer) ((JComboBox<String>)cb).getSelectedIndex()).toString())));
        }
    }
    private void componentPrefSave(JComponent cb, String name) {
        if (cb instanceof JCheckBox) {
            prefs.put(name, ((JCheckBox)cb).isSelected() ? "true" : "false");
        } else if (cb instanceof JTextField) {
            prefs.put(name, ((JTextField)cb).getText());
        } else if (cb instanceof JSpinner) {
            prefs.put(name, ((JSpinner)cb).getValue().toString());
        } else if (cb instanceof JComboBox) {
            prefs.put(name, ((Integer) ((JComboBox<String>)cb).getSelectedIndex()).toString());
        }
    }
    
    /**
     * Creates new form mainApplication
     */
    public mainApplication() {
        initComponents();
        DefaultCaret caret = (DefaultCaret)logTextarea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        listProfit.setModel(profitsChecker.getListProfitModel());
        listCurrencies.setModel(profitsChecker.getListCurrenciesModel());
        listHeroes.setModel(heroesController.getListHeroesModel());
        
        StrategiesController scontroller = new StrategiesController();
        List<String> s_items = new ArrayList<String>(scontroller.getStrategiesInitializerMap().keySet());
        java.util.Collections.sort(s_items);
        s_items.forEach((strategy_name)->{
            ComboBoxMainStrategy.addItem(strategy_name);
        });
        scontroller = null;
        
        prefs = Preferences.userNodeForPackage(this.getClass());
        setBounds(
            Integer.parseInt(prefs.get("window_pos_x", String.valueOf(Math.round(getBounds().getX())))),
            Integer.parseInt(prefs.get("window_pos_y", String.valueOf(Math.round(getBounds().getY())))),
            Integer.parseInt(prefs.get("window_size_x", String.valueOf(Math.round(getBounds().getWidth())))),
            Integer.parseInt(prefs.get("window_size_y", String.valueOf(Math.round(getBounds().getHeight()))))
        );
        componentPrefLoad(textFieldApiKey, "api_key");
        componentPrefLoad(textFieldApiSecret, "api_secret");
        componentPrefLoad(textFieldTradePairs, "trade_pairs");
        componentPrefLoad(checkboxTestMode, "test_mode");
        componentPrefLoad(checkBoxLowHold, "low_hold");
        componentPrefLoad(checkboxAutoFastorder, "auto_heroes");
        componentPrefLoad(checkBoxLimitedOrders, "limited_orders");
        componentPrefLoad(checkBoxCheckOtherStrategies, "strategies_add_check");
        componentPrefLoad(spinnerUpdateDelay, "update_delay");
        componentPrefLoad(spinnerBuyPercent, "buy_percent");
        componentPrefLoad(comboBoxBarsInterval, "bars");
        componentPrefLoad(ComboBoxMainStrategy, "main_strategy");
        componentPrefLoad(checkBoxStopGain, "use_stop_gain");
        componentPrefLoad(checkBoxStopLoss, "use_stop_loss");
        componentPrefLoad(spinnerStopGain, "stop_gain");
        componentPrefLoad(spinnerStopLoss, "stop_loss");
        componentPrefLoad(checkBoxBuyStopLimited, "use_buy_stop_limited_timeout");
        componentPrefLoad(spinnerBuyStopLimited, "buy_stop_limited_timeout");
        componentPrefLoad(checkBoxSellStopLimited, "use_sell_stop_limited_timeout");
        componentPrefLoad(spinnerSellStopLimited, "sell_stop_limited_timeout");
    }

    private int searchCurrencyFirstPair(String currencyPair, boolean is_hodling) {
        for(int i=0; i<pairs.size(); i++) {
            if (pairs.get(i).getSymbol().equals(currencyPair) && pairs.get(i).isHodling() == is_hodling) {
                return i;
            }
        }
        return -1;
    }
    private int searchCurrencyFirstPair(String currencyPair) {
        for(int i=0; i<pairs.size(); i++) {
            if (pairs.get(i).getSymbol().equals(currencyPair)) {
                return i;
            }
        }
        return -1;
    }
    
    public void log(String txt) {
        try {
            SEMAPHORE_LOG.acquire();
            logTextarea.append(txt);
            logTextarea.append("\n");
            SEMAPHORE_LOG.release();
        } catch (Exception e) {}
    }
    public void log(String txt, boolean is_main, boolean with_date) {
        try {
            SEMAPHORE_LOG.acquire();
            if (with_date) {
                Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                txt = formatter.format(Calendar.getInstance().getTime()) + ": " + txt;
            }
            logTextarea.append(txt);
            logTextarea.append("\n");
            if (is_main) {
                mainTextarea.append(txt);
                mainTextarea.append("\n");
            }
            SEMAPHORE_LOG.release();
        } catch (Exception e) {}
    }
    public void log(String txt, boolean is_main) {
        log(txt, is_main, false);
    }
    
    public List<tradePairProcess> getPairs() {
        return pairs;
    }
    public tradeProfitsController getProfitsChecker() {
        return profitsChecker;
    }
    
    private void initPairs() {
        pairs.forEach((pair)->{
            pair.set_do_remove_flag(true);
        });
        List<String> items = Arrays.asList(textFieldTradePairs.getText().toUpperCase().split("\\s*,\\s*"));
        if (items.size() > 0) {
            items.forEach((symbol)->{
                if (!symbol.isEmpty()) {
                    boolean has_plus = symbol.contains("+");
                    boolean has_2plus = symbol.contains("++");
                    boolean has_minus = !has_plus && symbol.contains("-");
                    symbol = symbol.replaceAll("\\-", "").replaceAll("\\+", "");
                    int str_index = ComboBoxMainStrategy.getSelectedIndex();
                    if (str_index < 0) {
                        str_index = 0;
                    }
                    int interval_index = comboBoxBarsInterval.getSelectedIndex();
                    if (interval_index < 0) {
                        interval_index = 0;
                    }
                    int pair_index = searchCurrencyFirstPair(symbol);
                    if (pair_index < 0) {
                        tradePairProcess nproc = new tradePairProcess(this, client, profitsChecker, symbol);
                        nproc.setTryingToSellUp(has_plus);
                        nproc.setSellUpAll(has_2plus);
                        nproc.setTryingToBuyDip(has_minus);
                        nproc.set_do_remove_flag(false);
                        nproc.setTradingBalancePercent((Integer) spinnerBuyPercent.getValue());
                        nproc.setMainStrategy(ComboBoxMainStrategy.getItemAt(str_index));
                        nproc.setBarInterval(comboBoxBarsInterval.getItemAt(interval_index));
                        nproc.setDelayTime((Integer) spinnerUpdateDelay.getValue());
                        nproc.setLowHold(checkBoxLowHold.isSelected());
                        nproc.setCheckOtherStrategies(checkBoxCheckOtherStrategies.isSelected());
                        nproc.setStartDelay(pairs.size() * 1000 + 500);
                        nproc.setUseBuyStopLimited(checkBoxBuyStopLimited.isSelected());
                        nproc.setStopBuyLimitTimeout((Integer) spinnerBuyStopLimited.getValue());
                        nproc.setUseSellStopLimited(checkBoxSellStopLimited.isSelected());
                        nproc.setStopSellLimitTimeout((Integer) spinnerSellStopLimited.getValue());
                        nproc.start();
                        pairs.add(nproc);
                    } else {
                        pairs.get(pair_index).setTryingToBuyDip(has_minus);
                        pairs.get(pair_index).set_do_remove_flag(false);
                        pairs.get(pair_index).setTradingBalancePercent((Integer) spinnerBuyPercent.getValue());
                        pairs.get(pair_index).setMainStrategy(ComboBoxMainStrategy.getItemAt(str_index));
                        pairs.get(pair_index).setBarInterval(comboBoxBarsInterval.getItemAt(interval_index));
                        pairs.get(pair_index).setDelayTime((Integer) spinnerUpdateDelay.getValue());
                        pairs.get(pair_index).setLowHold(checkBoxLowHold.isSelected());
                        pairs.get(pair_index).setCheckOtherStrategies(checkBoxCheckOtherStrategies.isSelected());
                        pairs.get(pair_index).setUseSellStopLimited(checkBoxBuyStopLimited.isSelected());
                        pairs.get(pair_index).setStopBuyLimitTimeout((Integer) spinnerBuyStopLimited.getValue());
                        pairs.get(pair_index).setUseSellStopLimited(checkBoxSellStopLimited.isSelected());
                        pairs.get(pair_index).setStopSellLimitTimeout((Integer) spinnerSellStopLimited.getValue());
                    }
                }
            });
        }
        int i = 0;
        while (i<pairs.size()) {
            if (pairs.get(i).is_do_remove_flag()) {
                pairs.get(i).doStop();
                profitsChecker.removeCurrencyPair(pairs.get(i).getSymbol());
                pairs.remove(i);
            } else {
                i++;
            }
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        logTextarea = new javax.swing.JTextArea();
        buttonRun = new javax.swing.JButton();
        buttonStop = new javax.swing.JButton();
        textFieldTradePairs = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        buttonClear = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        mainTextarea = new javax.swing.JTextArea();
        buttonPause = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        listCurrencies = new javax.swing.JList<>();
        jScrollPane4 = new javax.swing.JScrollPane();
        listProfit = new javax.swing.JList<>();
        buttonBuy = new javax.swing.JButton();
        buttonSell = new javax.swing.JButton();
        buttonSetPairs = new javax.swing.JButton();
        buttonUpdate = new javax.swing.JButton();
        buttonCancelLimit = new javax.swing.JButton();
        buttonShowPlot = new javax.swing.JButton();
        jScrollPane5 = new javax.swing.JScrollPane();
        listHeroes = new javax.swing.JList<>();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        textFieldApiSecret = new javax.swing.JTextField();
        textFieldApiKey = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel2 = new javax.swing.JPanel();
        spinnerUpdateDelay = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        checkboxAutoFastorder = new javax.swing.JCheckBox();
        checkboxTestMode = new javax.swing.JCheckBox();
        checkBoxLimitedOrders = new javax.swing.JCheckBox();
        checkBoxBuyStopLimited = new javax.swing.JCheckBox();
        spinnerBuyStopLimited = new javax.swing.JSpinner();
        jLabel12 = new javax.swing.JLabel();
        checkBoxSellStopLimited = new javax.swing.JCheckBox();
        spinnerSellStopLimited = new javax.swing.JSpinner();
        jLabel13 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        checkBoxStopGain = new javax.swing.JCheckBox();
        checkBoxStopLoss = new javax.swing.JCheckBox();
        spinnerStopLoss = new javax.swing.JSpinner();
        spinnerStopGain = new javax.swing.JSpinner();
        checkBoxLowHold = new javax.swing.JCheckBox();
        checkBoxCheckOtherStrategies = new javax.swing.JCheckBox();
        spinnerBuyPercent = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();
        ComboBoxMainStrategy = new javax.swing.JComboBox<>();
        jLabel4 = new javax.swing.JLabel();
        comboBoxBarsInterval = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        logTextarea.setColumns(20);
        logTextarea.setLineWrap(true);
        logTextarea.setRows(5);
        logTextarea.setWrapStyleWord(true);
        jScrollPane1.setViewportView(logTextarea);

        buttonRun.setText("Run");
        buttonRun.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonRunActionPerformed(evt);
            }
        });

        buttonStop.setText("Stop");
        buttonStop.setEnabled(false);
        buttonStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonStopActionPerformed(evt);
            }
        });

        textFieldTradePairs.setText("ltceth,rpxeth,xlmeth,neoeth,iotaeth,dasheth,adaeth");

        jLabel1.setText("Trading pairs");

        buttonClear.setText("Clear");
        buttonClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonClearActionPerformed(evt);
            }
        });

        mainTextarea.setColumns(20);
        mainTextarea.setLineWrap(true);
        mainTextarea.setRows(5);
        mainTextarea.setWrapStyleWord(true);
        jScrollPane2.setViewportView(mainTextarea);

        buttonPause.setText("Pause");
        buttonPause.setEnabled(false);
        buttonPause.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonPauseActionPerformed(evt);
            }
        });

        listCurrencies.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listCurrencies.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listCurrenciesMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(listCurrencies);

        listProfit.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane4.setViewportView(listProfit);

        buttonBuy.setText("Buy");
        buttonBuy.setEnabled(false);
        buttonBuy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonBuyActionPerformed(evt);
            }
        });

        buttonSell.setText("Sell");
        buttonSell.setEnabled(false);
        buttonSell.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonSellActionPerformed(evt);
            }
        });

        buttonSetPairs.setText("Set");
        buttonSetPairs.setEnabled(false);
        buttonSetPairs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonSetPairsActionPerformed(evt);
            }
        });

        buttonUpdate.setText("Update");
        buttonUpdate.setEnabled(false);
        buttonUpdate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonUpdateActionPerformed(evt);
            }
        });

        buttonCancelLimit.setText("Cancel limit order");
        buttonCancelLimit.setEnabled(false);
        buttonCancelLimit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonCancelLimitActionPerformed(evt);
            }
        });

        buttonShowPlot.setText("Show plot");
        buttonShowPlot.setEnabled(false);
        buttonShowPlot.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonShowPlotActionPerformed(evt);
            }
        });

        listHeroes.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listHeroes.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listHeroesMouseClicked(evt);
            }
        });
        jScrollPane5.setViewportView(listHeroes);

        jLabel6.setText("Log");

        jLabel7.setText("Buy & sell log");

        jLabel8.setText("Heroes");

        jLabel9.setText("Api Secret / Api Key:");

        jLabel10.setText("Trading currencies / pairs:");

        jLabel11.setText("Market base currencies:");

        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        spinnerUpdateDelay.setValue(10);

        jLabel5.setText("Update delay:");

        checkboxAutoFastorder.setText("Auto Heroes order");
        checkboxAutoFastorder.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkboxAutoFastorderStateChanged(evt);
            }
        });

        checkboxTestMode.setSelected(true);
        checkboxTestMode.setText("Test mode");
        checkboxTestMode.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkboxTestModeStateChanged(evt);
            }
        });

        checkBoxLimitedOrders.setText("Limited orders");
        checkBoxLimitedOrders.setEnabled(false);
        checkBoxLimitedOrders.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkBoxLimitedOrdersStateChanged(evt);
            }
        });

        checkBoxBuyStopLimited.setText("Stop limited buy orders if waiting more than:");
        checkBoxBuyStopLimited.setActionCommand("Stop limited  buy orders if waiting more than:");
        checkBoxBuyStopLimited.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkBoxBuyStopLimitedStateChanged(evt);
            }
        });

        spinnerBuyStopLimited.setEnabled(false);
        spinnerBuyStopLimited.setValue(120);

        jLabel12.setText("sec.");

        checkBoxSellStopLimited.setText("Stop limited sell orders if waiting more than:");
        checkBoxSellStopLimited.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkBoxSellStopLimitedStateChanged(evt);
            }
        });

        spinnerSellStopLimited.setEnabled(false);
        spinnerSellStopLimited.setValue(120);

        jLabel13.setText("sec.");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spinnerUpdateDelay, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(checkboxAutoFastorder))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(checkBoxLimitedOrders)
                            .addComponent(checkboxTestMode)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(checkBoxBuyStopLimited)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerBuyStopLimited, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel12))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(checkBoxSellStopLimited)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerSellStopLimited, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel13)))
                        .addGap(0, 46, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(checkboxAutoFastorder)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel5)
                        .addComponent(spinnerUpdateDelay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(18, 18, 18)
                .addComponent(checkboxTestMode)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkBoxLimitedOrders)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxBuyStopLimited)
                    .addComponent(spinnerBuyStopLimited, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxSellStopLimited)
                    .addComponent(spinnerSellStopLimited, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13))
                .addContainerGap(51, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Main", jPanel2);

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        checkBoxStopGain.setText("Stop Gain percent:");
        checkBoxStopGain.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkBoxStopGainStateChanged(evt);
            }
        });

        checkBoxStopLoss.setText("Stop Loss percent:");
        checkBoxStopLoss.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkBoxStopLossStateChanged(evt);
            }
        });

        spinnerStopLoss.setEnabled(false);
        spinnerStopLoss.setValue(3);

        spinnerStopGain.setEnabled(false);
        spinnerStopGain.setValue(30);

        checkBoxLowHold.setSelected(true);
        checkBoxLowHold.setText("Hold on low profit");
        checkBoxLowHold.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkBoxLowHoldStateChanged(evt);
            }
        });

        checkBoxCheckOtherStrategies.setText("Check other strategies");

        spinnerBuyPercent.setValue(100);

        jLabel3.setText("Buy percent:");

        ComboBoxMainStrategy.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Auto" }));

        jLabel4.setText("Main strategy:");

        comboBoxBarsInterval.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1m", "5m", "15m", "30m", "1h", "2h" }));

        jLabel2.setText("Bars interval:");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(2, 2, 2)
                                .addComponent(ComboBoxMainStrategy, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(spinnerBuyPercent, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(comboBoxBarsInterval, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(checkBoxStopGain)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerStopGain, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(checkBoxStopLoss)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerStopLoss, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(checkBoxLowHold)
                            .addComponent(checkBoxCheckOtherStrategies))))
                .addGap(0, 70, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(jLabel3))
                        .addGap(8, 8, 8)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(ComboBoxMainStrategy, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(spinnerBuyPercent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(comboBoxBarsInterval, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkBoxCheckOtherStrategies)
                .addGap(7, 7, 7)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxStopLoss)
                    .addComponent(spinnerStopLoss, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxStopGain)
                    .addComponent(spinnerStopGain, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(checkBoxLowHold)
                .addContainerGap(37, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Strategies", jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(buttonClear, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel9))
                        .addGap(161, 161, 161))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 254, Short.MAX_VALUE)
                            .addComponent(jLabel6)
                            .addComponent(jScrollPane1)
                            .addComponent(jLabel7)
                            .addComponent(textFieldApiKey, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(textFieldApiSecret, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel8)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 209, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3)
                    .addComponent(jScrollPane4)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(textFieldTradePairs)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonSetPairs))
                    .addComponent(jTabbedPane2)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(buttonRun, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonStop, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonPause, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(buttonBuy)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonSell)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonCancelLimit)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonUpdate)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonShowPlot))
                            .addComponent(jLabel1)
                            .addComponent(jLabel10)
                            .addComponent(jLabel11))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(textFieldTradePairs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(buttonSetPairs))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTabbedPane2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(buttonRun)
                            .addComponent(buttonStop)
                            .addComponent(buttonPause))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel10)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel9)
                            .addComponent(jLabel8))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane5)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(textFieldApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(textFieldApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel6)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(9, 9, 9)
                                .addComponent(jLabel7)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane2)))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buttonClear)
                    .addComponent(buttonBuy)
                    .addComponent(buttonSell)
                    .addComponent(buttonUpdate)
                    .addComponent(buttonCancelLimit)
                    .addComponent(buttonShowPlot)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void buttonRunActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonRunActionPerformed
        log("Starting...\n", true, true);
        is_paused = false;
        buttonRun.setEnabled(false);
        try {
            factory = BinanceApiClientFactory.newInstance(
                textFieldApiKey.getText(),
                textFieldApiSecret.getText()
            );
            client = factory.newRestClient();
            heroesController.setClient(client);
            heroesController.setLowHold(checkBoxLowHold.isSelected());
            heroesController.setAutoOrder(checkboxAutoFastorder.isSelected());
            profitsChecker.setClient(client);
            profitsChecker.setTestMode(checkboxTestMode.isSelected());
            profitsChecker.setLimitedOrders(checkBoxLimitedOrders.isSelected());
            profitsChecker.setStopGainPercent(checkBoxStopGain.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopGain.getValue()) : null);
            profitsChecker.setStopLossPercent(checkBoxStopLoss.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopLoss.getValue()) : null);
            profitsChecker.showTradeComissionCurrency();

            log("ServerTime = " + client.getServerTime());
            log("");
            log("Balances:", true);
            Account account = profitsChecker.getAccount();
            List<AssetBalance> allBalances = account.getBalances();        
            allBalances.forEach((balance)->{
                if (Float.parseFloat(balance.getFree()) > 0 || Float.parseFloat(balance.getLocked()) > 0) {
                    log(balance.getAsset() + " = " + balance.getFree() + " / " + balance.getLocked(), true);
                }
            });

            log("", true);
            log("Limits:", true);
            List<RateLimit> limits = client.getExchangeInfo().getRateLimits();
            limits.forEach((limit)->{
                log(limit.getRateLimitType().name() + " " + limit.getInterval().name() + " " + limit.getLimit(), true);
            });

            log("", true);
            heroesController.start();
            initPairs();

            checkboxTestMode.setEnabled(false);
            buttonStop.setEnabled(true);
            buttonPause.setEnabled(true);
            buttonBuy.setEnabled(true);
            buttonSell.setEnabled(true);
            buttonSetPairs.setEnabled(true);
            buttonUpdate.setEnabled(!checkboxTestMode.isSelected());
            buttonCancelLimit.setEnabled(checkBoxLimitedOrders.isSelected());
            buttonShowPlot.setEnabled(true);
        } catch (Exception e) {
            log("Error: " + e.getMessage(), true, true);
            e.printStackTrace();
            buttonRun.setEnabled(true);
        }
    }//GEN-LAST:event_buttonRunActionPerformed

    private void buttonStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonStopActionPerformed
        is_paused = false;
        buttonStop.setEnabled(false);
        buttonPause.setEnabled(false);
        if (pairs.size() > 0) {
            pairs.forEach((pair)->{
                pair.doStop();
            });
            pairs.clear();
        }
        listHeroes.setModel(new DefaultListModel<String>());
        heroesController.doStop();
        heroesController = null;
        buttonRun.setEnabled(true);
        checkboxTestMode.setEnabled(true);
        buttonBuy.setEnabled(false);
        buttonSell.setEnabled(false);
        buttonSetPairs.setEnabled(false);
        buttonUpdate.setEnabled(false);
        buttonCancelLimit.setEnabled(false);
        buttonShowPlot.setEnabled(false);
    }//GEN-LAST:event_buttonStopActionPerformed

    private void buttonClearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonClearActionPerformed
        logTextarea.setText("");
        mainTextarea.setText("");
    }//GEN-LAST:event_buttonClearActionPerformed

    private void buttonPauseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonPauseActionPerformed
        is_paused = !is_paused;
        buttonStop.setEnabled(true);
        buttonPause.setEnabled(true);
        buttonRun.setEnabled(false);
        buttonBuy.setEnabled(true);
        buttonSell.setEnabled(true);
        checkboxTestMode.setEnabled(false);
        buttonSetPairs.setEnabled(true);
        buttonUpdate.setEnabled(!checkboxTestMode.isSelected());
        buttonCancelLimit.setEnabled(checkBoxLimitedOrders.isSelected());
        buttonShowPlot.setEnabled(true);
        log(is_paused ? "Paused..." : "Unpaused");
        if (pairs.size() > 0) {
            pairs.forEach((pair)->{
                pair.doSetPaused(is_paused);
            });
        }
        heroesController.doSetPaused(is_paused);
    }//GEN-LAST:event_buttonPauseActionPerformed

    private void buttonBuyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonBuyActionPerformed
        if (pairs.size() > 0) {
            int curr_index = listCurrencies.getSelectedIndex();
            if (curr_index >= 0) {
                String currencyPair = profitsChecker.getNthCurrencyPair(curr_index);
                int pair_index = searchCurrencyFirstPair(currencyPair, false);
                if (pair_index >= 0 && pair_index < pairs.size()) {
                    pairs.get(pair_index).doBuy();
                }
            }
        }
    }//GEN-LAST:event_buttonBuyActionPerformed

    private void buttonSellActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSellActionPerformed
        if (pairs.size() > 0) {
            int curr_index = listCurrencies.getSelectedIndex();
            if (curr_index >= 0) {
                String currencyPair = profitsChecker.getNthCurrencyPair(curr_index);
                int pair_index = searchCurrencyFirstPair(currencyPair, true);
                if (pair_index >= 0 && pair_index < pairs.size()) {
                    pairs.get(pair_index).doSell();
                }
            }
        }
    }//GEN-LAST:event_buttonSellActionPerformed

    private void buttonSetPairsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSetPairsActionPerformed
        profitsChecker.setLimitedOrders(checkBoxLimitedOrders.isSelected());
        profitsChecker.setStopGainPercent(checkBoxStopGain.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopGain.getValue()) : null);
        profitsChecker.setStopLossPercent(checkBoxStopLoss.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopLoss.getValue()) : null);
        initPairs();
    }//GEN-LAST:event_buttonSetPairsActionPerformed

    private void buttonUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonUpdateActionPerformed
        if (pairs.size() > 0) {
            profitsChecker.updateAllBalances();
        }
    }//GEN-LAST:event_buttonUpdateActionPerformed

    private void buttonCancelLimitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonCancelLimitActionPerformed
        if (pairs.size() > 0) {
            int curr_index = listCurrencies.getSelectedIndex();
            if (curr_index >= 0) {
                String currencyPair = profitsChecker.getNthCurrencyPair(curr_index);
                int pair_index = searchCurrencyFirstPair(currencyPair);
                if (pair_index >= 0 && pair_index < pairs.size()) {
                    pairs.get(pair_index).doLimitCancel();
                }
            }
        }
    }//GEN-LAST:event_buttonCancelLimitActionPerformed

    private void buttonShowPlotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonShowPlotActionPerformed
        if (pairs.size() > 0) {
            int curr_index = listCurrencies.getSelectedIndex();
            if (curr_index >= 0) {
                String currencyPair = profitsChecker.getNthCurrencyPair(curr_index);
                int pair_index = searchCurrencyFirstPair(currencyPair);
                if (pair_index >= 0 && pair_index < pairs.size()) {
                    pairs.get(pair_index).doShowPlot();
                }
            }
        }
    }//GEN-LAST:event_buttonShowPlotActionPerformed

    private void checkBoxLimitedOrdersStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxLimitedOrdersStateChanged
        if (profitsChecker != null) {
            profitsChecker.setLimitedOrders(checkBoxLimitedOrders.isSelected());
        }
    }//GEN-LAST:event_checkBoxLimitedOrdersStateChanged

    private void listHeroesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listHeroesMouseClicked
        if (evt.getClickCount() == 2) {
            String content = listHeroes.getSelectedValue();
            if (content != null && !content.isEmpty()) {
                String[] parts = content.split(":");
                if (parts.length > 0) {
                    String newtext = parts[0].toLowerCase();
                    if (!textFieldTradePairs.getText().isEmpty()) {
                        newtext = textFieldTradePairs.getText() + "," + newtext;
                    }
                    textFieldTradePairs.setText(newtext);
                }
            }
        }
    }//GEN-LAST:event_listHeroesMouseClicked

    private void listCurrenciesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listCurrenciesMouseClicked
        if (evt.getClickCount() == 2) {
            buttonShowPlotActionPerformed(null);
        }
    }//GEN-LAST:event_listCurrenciesMouseClicked

    private void checkboxTestModeStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkboxTestModeStateChanged
        checkBoxLimitedOrders.setEnabled(!checkboxTestMode.isSelected());
    }//GEN-LAST:event_checkboxTestModeStateChanged

    private void checkBoxLowHoldStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxLowHoldStateChanged
        if (heroesController != null) {
            heroesController.setLowHold(checkBoxLowHold.isSelected());
        }
    }//GEN-LAST:event_checkBoxLowHoldStateChanged

    private void checkboxAutoFastorderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkboxAutoFastorderStateChanged
        if (heroesController != null) {
            heroesController.setAutoOrder(checkboxAutoFastorder.isSelected());
        }
    }//GEN-LAST:event_checkboxAutoFastorderStateChanged

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        prefs.put("window_pos_x", String.valueOf(Math.round(getBounds().getX())));
        prefs.put("window_pos_y", String.valueOf(Math.round(getBounds().getY())));
        prefs.put("window_size_x", String.valueOf(Math.round(getBounds().getWidth())));
        prefs.put("window_size_y", String.valueOf(Math.round(getBounds().getHeight())));
        componentPrefSave(textFieldApiKey, "api_key");
        componentPrefSave(textFieldApiSecret, "api_secret");
        componentPrefSave(textFieldTradePairs, "trade_pairs");
        componentPrefSave(checkboxTestMode, "test_mode");
        componentPrefSave(checkBoxLowHold, "low_hold");
        componentPrefSave(checkboxAutoFastorder, "auto_heroes");
        componentPrefSave(checkBoxLimitedOrders, "limited_orders");
        componentPrefSave(checkBoxCheckOtherStrategies, "strategies_add_check");
        componentPrefSave(spinnerUpdateDelay, "update_delay");
        componentPrefSave(spinnerBuyPercent, "buy_percent");
        componentPrefSave(comboBoxBarsInterval, "bars");
        componentPrefSave(ComboBoxMainStrategy, "main_strategy");
        componentPrefSave(checkBoxStopGain, "use_stop_gain");
        componentPrefSave(checkBoxStopLoss, "use_stop_loss");
        componentPrefSave(spinnerStopGain, "stop_gain");
        componentPrefSave(spinnerStopLoss, "stop_loss");
        componentPrefSave(checkBoxBuyStopLimited, "use_buy_stop_limited_timeout");
        componentPrefSave(spinnerBuyStopLimited, "buy_stop_limited_timeout");
        componentPrefSave(checkBoxSellStopLimited, "use_sell_stop_limited_timeout");
        componentPrefSave(spinnerSellStopLimited, "sell_stop_limited_timeout");
    }//GEN-LAST:event_formWindowClosing

    private void checkBoxStopLossStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxStopLossStateChanged
        spinnerStopLoss.setEnabled(checkBoxStopLoss.isSelected());
    }//GEN-LAST:event_checkBoxStopLossStateChanged

    private void checkBoxStopGainStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxStopGainStateChanged
        spinnerStopGain.setEnabled(checkBoxStopGain.isSelected());
    }//GEN-LAST:event_checkBoxStopGainStateChanged

    private void checkBoxBuyStopLimitedStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxBuyStopLimitedStateChanged
        spinnerBuyStopLimited.setEnabled(checkBoxBuyStopLimited.isSelected());
    }//GEN-LAST:event_checkBoxBuyStopLimitedStateChanged

    private void checkBoxSellStopLimitedStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxSellStopLimitedStateChanged
        spinnerSellStopLimited.setEnabled(checkBoxSellStopLimited.isSelected());
    }//GEN-LAST:event_checkBoxSellStopLimitedStateChanged

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(mainApplication.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(mainApplication.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(mainApplication.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(mainApplication.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new mainApplication().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> ComboBoxMainStrategy;
    private javax.swing.JButton buttonBuy;
    private javax.swing.JButton buttonCancelLimit;
    private javax.swing.JButton buttonClear;
    private javax.swing.JButton buttonPause;
    private javax.swing.JButton buttonRun;
    private javax.swing.JButton buttonSell;
    private javax.swing.JButton buttonSetPairs;
    private javax.swing.JButton buttonShowPlot;
    private javax.swing.JButton buttonStop;
    private javax.swing.JButton buttonUpdate;
    private javax.swing.JCheckBox checkBoxBuyStopLimited;
    private javax.swing.JCheckBox checkBoxCheckOtherStrategies;
    private javax.swing.JCheckBox checkBoxLimitedOrders;
    private javax.swing.JCheckBox checkBoxLowHold;
    private javax.swing.JCheckBox checkBoxSellStopLimited;
    private javax.swing.JCheckBox checkBoxStopGain;
    private javax.swing.JCheckBox checkBoxStopLoss;
    private javax.swing.JCheckBox checkboxAutoFastorder;
    private javax.swing.JCheckBox checkboxTestMode;
    private javax.swing.JComboBox<String> comboBoxBarsInterval;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JList<String> listCurrencies;
    private javax.swing.JList<String> listHeroes;
    private javax.swing.JList<String> listProfit;
    private javax.swing.JTextArea logTextarea;
    private javax.swing.JTextArea mainTextarea;
    private javax.swing.JSpinner spinnerBuyPercent;
    private javax.swing.JSpinner spinnerBuyStopLimited;
    private javax.swing.JSpinner spinnerSellStopLimited;
    private javax.swing.JSpinner spinnerStopGain;
    private javax.swing.JSpinner spinnerStopLoss;
    private javax.swing.JSpinner spinnerUpdateDelay;
    private javax.swing.JTextField textFieldApiKey;
    private javax.swing.JTextField textFieldApiSecret;
    private javax.swing.JTextField textFieldTradePairs;
    // End of variables declaration//GEN-END:variables

}
