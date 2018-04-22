/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.api.TradingAPIBinance;
import com.evgcompany.binntrdbot.coinrating.*;
import com.evgcompany.binntrdbot.misc.ComponentsConfigController;
import com.evgcompany.binntrdbot.signal.SignalController;
import com.evgcompany.binntrdbot.strategies.core.StrategiesController;
import java.awt.Toolkit;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.text.DefaultCaret;

/**
 *
 * @author EVG_adm_T
 */
public class mainApplication extends javax.swing.JFrame {

    private TradingAPIAbstractInterface client = null;
    
    private static final Semaphore SEMAPHORE_LOG = new Semaphore(1, true);
    
    private final ComponentsConfigController config = new ComponentsConfigController(this);
    private final tradeProfitsController profitsChecker = new tradeProfitsController(this);
    private final tradePairProcessController pairController = new tradePairProcessController(this, profitsChecker);
    private final CoinRatingController coinRatingController = new CoinRatingController(this, pairController);
    
    private boolean is_paused = false;
    
    private static volatile mainApplication instance = null;
    
    private static final DecimalFormat df3 = new DecimalFormat("0.##");
    
    public static mainApplication getInstance() {
        return instance;
    }
    
    public void systemSound() {
        final Runnable runnable =
            (Runnable) Toolkit.getDefaultToolkit().getDesktopProperty("win.sound.exclamation");
       if (runnable != null) runnable.run();
    }
    
    /**
     * Creates new form mainApplication
     */
    public mainApplication() {
        instance = this;
        initComponents();
        DefaultCaret caret = (DefaultCaret)logTextarea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        listProfit.setModel(profitsChecker.getListProfitModel());
        listCurrencies.setModel(profitsChecker.getListCurrenciesModel());
        listRating.setModel(coinRatingController.getListHeroesModel());
        listBoxAutoStrategies.setModel(new DefaultListModel<String>());
        new StrategiesController().getStrategiesNames().forEach((strategy_name)->{
            ComboBoxMainStrategy.addItem(strategy_name);
            ((DefaultListModel)listBoxAutoStrategies.getModel()).addElement(strategy_name);
        });
        
        coinRatingController.setTrendUpdateEvent((upt, dnt) -> {
            labelUpTrend.setText("UP: " + df3.format(upt) + "%");
            labelDownTrend.setText("DOWN: " + df3.format(dnt) + "%");
        });
        
        config.addComponent(instance, "window");
        config.addComponent(textFieldApiKey, "api_key");
        config.addComponent(textFieldApiSecret, "api_secret");
        config.addComponent(textFieldTradePairs, "trade_pairs");
        config.addComponent(checkboxTestMode, "test_mode");
        config.addComponent(checkBoxLowHold, "low_hold");
        config.addComponent(checkboxAutoOrder, "auto_order");
        config.addComponent(checkboxAutoFastorder, "auto_fastorder");
        config.addComponent(checkBoxAutoAnalyzer, "auto_anal");
        config.addComponent(checkBoxLimitedOrders, "limited_orders");
        config.addComponent(checkBoxCheckOtherStrategies, "strategies_add_check");
        config.addComponent(spinnerUpdateDelay, "update_delay");
        config.addComponent(spinnerBuyPercent, "buy_percent");
        config.addComponent(comboBoxBarsInterval, "bars");
        config.addComponent(comboBoxBarsCount, "bars_queries_index");
        config.addComponent(ComboBoxMainStrategy, "main_strategy");
        config.addComponent(checkBoxStopGain, "use_stop_gain");
        config.addComponent(checkBoxStopLoss, "use_stop_loss");
        config.addComponent(spinnerStopGain, "stop_gain");
        config.addComponent(spinnerStopLoss, "stop_loss");
        config.addComponent(checkBoxBuyStopLimited, "use_buy_stop_limited_timeout");
        config.addComponent(spinnerBuyStopLimited, "buy_stop_limited_timeout");
        config.addComponent(checkBoxSellStopLimited, "use_sell_stop_limited_timeout");
        config.addComponent(spinnerSellStopLimited, "sell_stop_limited_timeout");
        config.addComponent(comboBoxLimitedMode, "limited_mode");
        config.addComponent(textFieldAPIID, "signals_api_id");
        config.addComponent(textFieldAPIHash, "signals_api_hash");
        config.addComponent(textFieldPhone, "signals_api_phone");
        config.addComponent(checkboxAutoSignalOrder, "auto_signals_order");
        config.addComponent(checkboxAutoSignalFastorder, "auto_signals_fast_order");
        config.addComponent(spinnerScanRatingDelayTime, "rating_scan_delay_time");
        config.addComponent(spinnerScanRatingUpdateTime, "rating_scan_update_time");
        config.addComponent(spinnerRatingMaxOrders, "rating_max_order_count");
        config.addComponent(spinnerRatingMaxOrderWait, "rating_max_order_wait");
        config.addComponent(spinnerRatingMinForOrder, "rating_min_for_order");
        config.addComponent(listBoxAutoStrategies, "auto_strategies_list");
        config.addComponent(checkBoxWalkForward, "auto_walkforward");
        config.addComponent(checkBoxAutoStrategyParams, "auto_strategyparamspick");
        config.addComponent(spinnerSignalRatingMinForOrder, "signal_rating_min_for_order");
        config.addComponent(spinnerSignalPreloadCount, "signal_preload_count");
        config.addComponent(spinnerMaxSignalOrders, "signal_max_orders_count");
        config.addComponent(checkBoxDowntrendNoAuto, "rating_no_auto_on_downtrend");
        config.Load();
    }
    
    public void log(String txt) {
        try {
            SEMAPHORE_LOG.acquire();
            logTextarea.append(txt);
            logTextarea.append("\n");
            SEMAPHORE_LOG.release();
        } catch (InterruptedException e) {}
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
        } catch (InterruptedException e) {}
    }
    public void log(String txt, boolean is_main) {
        log(txt, is_main, false);
    }
    
    public tradeProfitsController getProfitsChecker() {
        return profitsChecker;
    }
    
    public tradePairProcessController getPairController() {
        return pairController;
    }
    
    public CoinRatingController getCoinRatingController() {
        return coinRatingController;
    }

    private void initAPI() {
        if (client == null) {
            client = new TradingAPIBinance(textFieldApiSecret.getText(), textFieldApiKey.getText());
            client.connect();
            profitsChecker.setClient(client);
            coinRatingController.setClient(client);
        }
    }
    
    private void setPairParams() {
        int str_index = ComboBoxMainStrategy.getSelectedIndex();
        if (str_index < 0) str_index = 0;
        pairController.setMainStrategy(ComboBoxMainStrategy.getItemAt(str_index));
        int bq_index = comboBoxBarsCount.getSelectedIndex();
        if (bq_index < 0) bq_index = 0;
        pairController.setBarAdditionalCount(Integer.parseInt(comboBoxBarsCount.getItemAt(bq_index)) / 500);
        int interval_index = comboBoxBarsInterval.getSelectedIndex();
        if (interval_index < 0) interval_index = 0;
        pairController.setBarsInterval(comboBoxBarsInterval.getItemAt(interval_index));
        pairController.setTradingBalancePercent((Integer) spinnerBuyPercent.getValue());
        pairController.setBuyStop(checkBoxBuyStopLimited.isSelected());
        pairController.setSellStop(checkBoxSellStopLimited.isSelected());
        pairController.setBuyStopLimitedTimeout((Integer) spinnerBuyStopLimited.getValue());
        pairController.setSellStopLimitedTimeout((Integer) spinnerSellStopLimited.getValue());
        pairController.setUpdateDelay((Integer) spinnerUpdateDelay.getValue());
        pairController.setCheckOtherStrategies(checkBoxCheckOtherStrategies.isSelected());
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
        listRating = new javax.swing.JList<>();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel2 = new javax.swing.JPanel();
        spinnerUpdateDelay = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        checkboxTestMode = new javax.swing.JCheckBox();
        checkBoxLimitedOrders = new javax.swing.JCheckBox();
        checkBoxBuyStopLimited = new javax.swing.JCheckBox();
        spinnerBuyStopLimited = new javax.swing.JSpinner();
        jLabel12 = new javax.swing.JLabel();
        checkBoxSellStopLimited = new javax.swing.JCheckBox();
        spinnerSellStopLimited = new javax.swing.JSpinner();
        jLabel13 = new javax.swing.JLabel();
        comboBoxLimitedMode = new javax.swing.JComboBox<>();
        textFieldApiSecret = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        textFieldApiKey = new javax.swing.JTextField();
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
        jLabel14 = new javax.swing.JLabel();
        comboBoxBarsCount = new javax.swing.JComboBox<>();
        jScrollPane7 = new javax.swing.JScrollPane();
        listBoxAutoStrategies = new javax.swing.JList<>();
        jLabel23 = new javax.swing.JLabel();
        checkBoxWalkForward = new javax.swing.JCheckBox();
        checkBoxAutoStrategyParams = new javax.swing.JCheckBox();
        jPanel4 = new javax.swing.JPanel();
        checkboxAutoOrder = new javax.swing.JCheckBox();
        checkBoxAutoAnalyzer = new javax.swing.JCheckBox();
        spinnerScanRatingDelayTime = new javax.swing.JSpinner();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        spinnerScanRatingUpdateTime = new javax.swing.JSpinner();
        checkboxAutoFastorder = new javax.swing.JCheckBox();
        jLabel19 = new javax.swing.JLabel();
        spinnerRatingMaxOrders = new javax.swing.JSpinner();
        jLabel21 = new javax.swing.JLabel();
        spinnerRatingMaxOrderWait = new javax.swing.JSpinner();
        jLabel22 = new javax.swing.JLabel();
        spinnerRatingMinForOrder = new javax.swing.JSpinner();
        checkBoxDowntrendNoAuto = new javax.swing.JCheckBox();
        jPanel5 = new javax.swing.JPanel();
        textFieldAPIID = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        textFieldAPIHash = new javax.swing.JTextField();
        buttonTlgConnect = new javax.swing.JButton();
        jLabel20 = new javax.swing.JLabel();
        textFieldPhone = new javax.swing.JTextField();
        checkboxAutoSignalOrder = new javax.swing.JCheckBox();
        checkboxAutoSignalFastorder = new javax.swing.JCheckBox();
        jLabel24 = new javax.swing.JLabel();
        spinnerSignalRatingMinForOrder = new javax.swing.JSpinner();
        jLabel25 = new javax.swing.JLabel();
        spinnerSignalPreloadCount = new javax.swing.JSpinner();
        jLabel26 = new javax.swing.JLabel();
        spinnerMaxSignalOrders = new javax.swing.JSpinner();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane6 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        ButtonStatistics = new javax.swing.JButton();
        buttonWebBrowserOpen = new javax.swing.JButton();
        comboBoxRatingSortby = new javax.swing.JComboBox<>();
        comboBoxRatingSort = new javax.swing.JComboBox<>();
        buttonRatingStart = new javax.swing.JButton();
        buttonRatingStop = new javax.swing.JButton();
        buttonRatingCheck = new javax.swing.JButton();
        progressBarRatingAnalPercent = new javax.swing.JProgressBar();
        buttonNNBTrain = new javax.swing.JButton();
        buttonNNBAdd = new javax.swing.JButton();
        buttonNNCTrain = new javax.swing.JButton();
        buttonNNCAdd = new javax.swing.JButton();
        buttonRemove = new javax.swing.JButton();
        labelUpTrend = new javax.swing.JLabel();
        labelDownTrend = new javax.swing.JLabel();

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

        buttonBuy.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonBuy.setText("Buy");
        buttonBuy.setToolTipText("Buy selected coin");
        buttonBuy.setEnabled(false);
        buttonBuy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonBuyActionPerformed(evt);
            }
        });

        buttonSell.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonSell.setText("Sell");
        buttonSell.setToolTipText("Sell selected coin");
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

        buttonUpdate.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonUpdate.setText("Update");
        buttonUpdate.setToolTipText("Update balances");
        buttonUpdate.setEnabled(false);
        buttonUpdate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonUpdateActionPerformed(evt);
            }
        });

        buttonCancelLimit.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonCancelLimit.setText("Cancel LO");
        buttonCancelLimit.setToolTipText("Cancel limit order");
        buttonCancelLimit.setEnabled(false);
        buttonCancelLimit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonCancelLimitActionPerformed(evt);
            }
        });

        buttonShowPlot.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonShowPlot.setText("Show plot");
        buttonShowPlot.setToolTipText("Show plot");
        buttonShowPlot.setEnabled(false);
        buttonShowPlot.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonShowPlotActionPerformed(evt);
            }
        });

        listRating.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listRating.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listRatingMouseClicked(evt);
            }
        });
        jScrollPane5.setViewportView(listRating);

        jLabel6.setText("Log");

        jLabel7.setText("Buy & sell log");

        jLabel8.setText("Coins rating");

        jLabel10.setText("Trading currencies / pairs:");

        jLabel11.setText("Market base currencies:");

        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        spinnerUpdateDelay.setValue(10);

        jLabel5.setText("Update delay:");

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
        checkBoxLimitedOrders.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxLimitedOrdersActionPerformed(evt);
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

        comboBoxLimitedMode.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Only Sell", "Sell and Buy" }));
        comboBoxLimitedMode.setEnabled(false);
        comboBoxLimitedMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxLimitedModeActionPerformed(evt);
            }
        });

        jLabel9.setText("Api Secret / Api Key:");

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
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel9)
                            .addComponent(textFieldApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(checkBoxLimitedOrders)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboBoxLimitedMode, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE))
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
                        .addGap(0, 152, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(checkboxTestMode)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(textFieldApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel5)
                        .addComponent(spinnerUpdateDelay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(textFieldApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkboxTestMode)
                    .addComponent(textFieldApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxLimitedOrders)
                    .addComponent(comboBoxLimitedMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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
                .addContainerGap(75, Short.MAX_VALUE))
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

        jLabel3.setText("Buy %:");

        ComboBoxMainStrategy.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Auto" }));

        jLabel4.setText("Main strategy:");

        comboBoxBarsInterval.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1m", "5m", "15m", "30m", "1h", "2h" }));

        jLabel2.setText("Bars interval:");

        jLabel14.setText("Bars count:");

        comboBoxBarsCount.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "500", "1000", "1500", "2000", "3000", "4000", "5000" }));

        jScrollPane7.setViewportView(listBoxAutoStrategies);

        jLabel23.setText("Strategies for auto select:");

        checkBoxWalkForward.setText("WalkForward");
        checkBoxWalkForward.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxWalkForwardActionPerformed(evt);
            }
        });

        checkBoxAutoStrategyParams.setText("Auto pick strategy params");
        checkBoxAutoStrategyParams.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxAutoStrategyParamsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel4)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(ComboBoxMainStrategy, 0, 226, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel3)
                                    .addComponent(spinnerBuyPercent, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(comboBoxBarsInterval, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jLabel2))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(comboBoxBarsCount, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel14)))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(checkBoxCheckOtherStrategies)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(checkBoxStopGain)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(spinnerStopGain, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(checkBoxStopLoss)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(spinnerStopLoss, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(checkBoxLowHold)
                                    .addComponent(checkBoxAutoStrategyParams))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel23)
                                    .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(checkBoxWalkForward))))))
                .addContainerGap(39, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addGap(8, 8, 8)
                        .addComponent(ComboBoxMainStrategy, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addGap(8, 8, 8)
                        .addComponent(spinnerBuyPercent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel2)
                            .addComponent(jLabel14))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(comboBoxBarsInterval, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(comboBoxBarsCount, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxCheckOtherStrategies)
                    .addComponent(jLabel23))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(checkBoxStopLoss)
                            .addComponent(spinnerStopLoss, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(checkBoxStopGain)
                            .addComponent(spinnerStopGain, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkBoxLowHold))
                    .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxAutoStrategyParams)
                    .addComponent(checkBoxWalkForward))
                .addContainerGap(45, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Strategies", jPanel1);

        checkboxAutoOrder.setText("Auto order");
        checkboxAutoOrder.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                checkboxAutoOrderStateChanged(evt);
            }
        });
        checkboxAutoOrder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkboxAutoOrderActionPerformed(evt);
            }
        });

        checkBoxAutoAnalyzer.setText("Analyzer");

        spinnerScanRatingDelayTime.setValue(2);
        spinnerScanRatingDelayTime.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerScanRatingDelayTimeStateChanged(evt);
            }
        });

        jLabel15.setText("Next coin check delay time:");

        jLabel16.setText("Update time:");

        spinnerScanRatingUpdateTime.setValue(10);

        checkboxAutoFastorder.setText("Fast auto order");
        checkboxAutoFastorder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkboxAutoFastorderActionPerformed(evt);
            }
        });

        jLabel19.setText("Max orders:");

        spinnerRatingMaxOrders.setValue(3);

        jLabel21.setText("Max order wait (sec):");

        spinnerRatingMaxOrderWait.setValue(600);

        jLabel22.setText("Min rating for order:");

        spinnerRatingMinForOrder.setValue(5);

        checkBoxDowntrendNoAuto.setText("No auto orders on downtrend");
        checkBoxDowntrendNoAuto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxDowntrendNoAutoActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(checkBoxAutoAnalyzer)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(checkboxAutoOrder)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(checkboxAutoFastorder))
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(jLabel16)
                                    .addComponent(jLabel15))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(spinnerScanRatingDelayTime, javax.swing.GroupLayout.DEFAULT_SIZE, 82, Short.MAX_VALUE)
                                    .addComponent(spinnerScanRatingUpdateTime)))
                            .addComponent(checkBoxDowntrendNoAuto))
                        .addGap(25, 25, 25)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel21)
                            .addComponent(jLabel19)
                            .addComponent(jLabel22))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(spinnerRatingMinForOrder, javax.swing.GroupLayout.DEFAULT_SIZE, 86, Short.MAX_VALUE)
                            .addComponent(spinnerRatingMaxOrders)
                            .addComponent(spinnerRatingMaxOrderWait, javax.swing.GroupLayout.DEFAULT_SIZE, 86, Short.MAX_VALUE))))
                .addGap(45, 45, 45))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(checkBoxAutoAnalyzer)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(checkboxAutoOrder)
                            .addComponent(checkboxAutoFastorder))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(spinnerScanRatingDelayTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel15))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel16)
                            .addComponent(spinnerScanRatingUpdateTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(40, 40, 40)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(spinnerRatingMaxOrders, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel19))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel21)
                            .addComponent(spinnerRatingMaxOrderWait, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel22)
                            .addComponent(spinnerRatingMinForOrder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(18, 18, 18)
                .addComponent(checkBoxDowntrendNoAuto)
                .addContainerGap(78, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Coins rating", jPanel4);

        jLabel17.setText("API ID:");

        jLabel18.setText("API Hash:");

        buttonTlgConnect.setText("Test");
        buttonTlgConnect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonTlgConnectActionPerformed(evt);
            }
        });

        jLabel20.setText("Phone:");

        checkboxAutoSignalOrder.setText("Auto order");
        checkboxAutoSignalOrder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkboxAutoSignalOrderActionPerformed(evt);
            }
        });

        checkboxAutoSignalFastorder.setText("Fast auto order");
        checkboxAutoSignalFastorder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkboxAutoSignalFastorderActionPerformed(evt);
            }
        });

        jLabel24.setText("Min rating for order:");

        spinnerSignalRatingMinForOrder.setValue(5);

        jLabel25.setText("Preload signals:");

        spinnerSignalPreloadCount.setValue(200);

        jLabel26.setText("Max orders:");

        spinnerMaxSignalOrders.setValue(7);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel17)
                            .addComponent(textFieldAPIID, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel18)
                            .addComponent(textFieldAPIHash, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(textFieldPhone, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonTlgConnect))
                            .addComponent(jLabel20)))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(jLabel24)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerSignalRatingMinForOrder, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(checkboxAutoSignalOrder)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(checkboxAutoSignalFastorder))
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(jLabel25)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerSignalPreloadCount, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(18, 18, 18)
                        .addComponent(jLabel26)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spinnerMaxSignalOrders, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(38, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(jLabel18)
                    .addComponent(jLabel20))
                .addGap(6, 6, 6)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(textFieldAPIID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(textFieldAPIHash, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(textFieldPhone, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buttonTlgConnect))
                .addGap(18, 18, 18)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkboxAutoSignalOrder)
                    .addComponent(checkboxAutoSignalFastorder))
                .addGap(18, 18, 18)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(spinnerMaxSignalOrders, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel26))
                    .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel24)
                        .addComponent(spinnerSignalRatingMinForOrder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel25)
                    .addComponent(spinnerSignalPreloadCount, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(75, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Signals", jPanel5);

        jTextArea1.setColumns(20);
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText("Modifiers:\nBuy for best price: -\nSell free ordered (or opened orders if no free): +\nSell all (free + opened orders): ++");
        jTextArea1.setWrapStyleWord(true);
        jScrollPane6.setViewportView(jTextArea1);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 470, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 217, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane2.addTab("Tips", jPanel3);

        ButtonStatistics.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        ButtonStatistics.setText("Statistics");
        ButtonStatistics.setToolTipText("Show coin strategy statistics");
        ButtonStatistics.setEnabled(false);
        ButtonStatistics.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonStatisticsActionPerformed(evt);
            }
        });

        buttonWebBrowserOpen.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonWebBrowserOpen.setText("Binance");
        buttonWebBrowserOpen.setToolTipText("Open coin page");
        buttonWebBrowserOpen.setEnabled(false);
        buttonWebBrowserOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonWebBrowserOpenActionPerformed(evt);
            }
        });

        comboBoxRatingSortby.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Rank", "Market cap", "% from prog start", "% last hour", "% 24hr", "Events count", "Last event anno date", "Volatility", "Strategies to enter value", "Strategies to exit value", "Strategies value", "Signals rating", "Calculated rating" }));
        comboBoxRatingSortby.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxRatingSortbyActionPerformed(evt);
            }
        });

        comboBoxRatingSort.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "ASC", "DESC" }));
        comboBoxRatingSort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxRatingSortActionPerformed(evt);
            }
        });

        buttonRatingStart.setText("Start");
        buttonRatingStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonRatingStartActionPerformed(evt);
            }
        });

        buttonRatingStop.setText("Stop");
        buttonRatingStop.setEnabled(false);
        buttonRatingStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonRatingStopActionPerformed(evt);
            }
        });

        buttonRatingCheck.setText("Check");
        buttonRatingCheck.setEnabled(false);

        buttonNNBTrain.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonNNBTrain.setText("NN B Train");
        buttonNNBTrain.setToolTipText("Neural network Base Train");
        buttonNNBTrain.setEnabled(false);
        buttonNNBTrain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonNNBTrainActionPerformed(evt);
            }
        });

        buttonNNBAdd.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonNNBAdd.setText("NN B Add");
        buttonNNBAdd.setToolTipText("Neural network Base Add to dataset");
        buttonNNBAdd.setEnabled(false);
        buttonNNBAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonNNBAddActionPerformed(evt);
            }
        });

        buttonNNCTrain.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonNNCTrain.setText("NN C Train");
        buttonNNCTrain.setToolTipText("Neural network Coin Train");
        buttonNNCTrain.setEnabled(false);
        buttonNNCTrain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonNNCTrainActionPerformed(evt);
            }
        });

        buttonNNCAdd.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonNNCAdd.setText("NN C Add");
        buttonNNCAdd.setToolTipText("Neural network Coin add to dataset");
        buttonNNCAdd.setEnabled(false);
        buttonNNCAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonNNCAddActionPerformed(evt);
            }
        });

        buttonRemove.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        buttonRemove.setText("Remove");
        buttonRemove.setToolTipText("Remove selected coin");
        buttonRemove.setEnabled(false);
        buttonRemove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonRemoveActionPerformed(evt);
            }
        });

        labelUpTrend.setForeground(new java.awt.Color(0, 204, 51));
        labelUpTrend.setText("UP: 0%");

        labelDownTrend.setForeground(new java.awt.Color(255, 0, 102));
        labelDownTrend.setText("DOWN: 0%");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(buttonClear, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(175, 175, 175))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel6)
                                    .addComponent(jLabel7))
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addComponent(jScrollPane2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(comboBoxRatingSortby, javax.swing.GroupLayout.PREFERRED_SIZE, 129, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboBoxRatingSort, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(progressBarRatingAnalPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel8)
                                .addGap(152, 152, 152))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(labelUpTrend, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(labelDownTrend)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(textFieldTradePairs)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonSetPairs))
                            .addComponent(jTabbedPane2)
                            .addComponent(jScrollPane3)
                            .addComponent(jScrollPane4)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(buttonRun, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonStop, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonPause, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel1)
                            .addComponent(jLabel10)
                            .addComponent(jLabel11)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(buttonBuy, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(buttonWebBrowserOpen, javax.swing.GroupLayout.DEFAULT_SIZE, 80, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(buttonNNBTrain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(buttonSell, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(buttonNNCTrain, javax.swing.GroupLayout.DEFAULT_SIZE, 80, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(buttonNNBAdd, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(buttonCancelLimit, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(buttonNNCAdd, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(buttonUpdate, javax.swing.GroupLayout.DEFAULT_SIZE, 80, Short.MAX_VALUE)
                                    .addComponent(buttonRemove, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGap(78, 78, 78)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(buttonShowPlot, javax.swing.GroupLayout.DEFAULT_SIZE, 79, Short.MAX_VALUE)
                                    .addComponent(ButtonStatistics, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(buttonRatingStart)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonRatingStop)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonRatingCheck)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel8)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(comboBoxRatingSortby, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(comboBoxRatingSort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(labelUpTrend)
                                    .addComponent(labelDownTrend))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(progressBarRatingAnalPercent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel6)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel7)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane2)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(buttonClear)
                            .addComponent(buttonRatingStart)
                            .addComponent(buttonRatingStop)
                            .addComponent(buttonRatingCheck)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(textFieldTradePairs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(buttonSetPairs))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTabbedPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 267, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 116, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(buttonBuy)
                            .addComponent(buttonSell)
                            .addComponent(buttonUpdate)
                            .addComponent(buttonCancelLimit)
                            .addComponent(buttonShowPlot))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(buttonWebBrowserOpen)
                            .addComponent(ButtonStatistics)
                            .addComponent(buttonNNBTrain)
                            .addComponent(buttonNNBAdd)
                            .addComponent(buttonRemove))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(buttonNNCTrain)
                            .addComponent(buttonNNCAdd))
                        .addGap(15, 15, 15))))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void buttonRunActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonRunActionPerformed
        log("Starting...\n", true, true);
        is_paused = false;
        buttonRun.setEnabled(false);
        try {
            initAPI();
            profitsChecker.setAutoPickStrategyParams(checkBoxAutoStrategyParams.isSelected());
            profitsChecker.setAutoWalkForward(checkBoxWalkForward.isSelected());
            profitsChecker.setAutoStrategies(listBoxAutoStrategies.getSelectedValuesList());
            profitsChecker.setTestMode(checkboxTestMode.isSelected());
            profitsChecker.setLimitedOrders(checkBoxLimitedOrders.isSelected());
            profitsChecker.setLimitedOrderMode(comboBoxLimitedMode.getSelectedIndex() == 0 ? tradeProfitsController.LimitedOrderMode.LOMODE_SELL : tradeProfitsController.LimitedOrderMode.LOMODE_SELLANDBUY);
            profitsChecker.setStopGainPercent(checkBoxStopGain.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopGain.getValue()) : null);
            profitsChecker.setStopLossPercent(checkBoxStopLoss.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopLoss.getValue()) : null);
            profitsChecker.setLowHold(checkBoxLowHold.isSelected());
            profitsChecker.showTradeComissionCurrency();

            log("", true);
            setPairParams();
            pairController.initBasePairs(textFieldTradePairs.getText());
            checkboxTestMode.setEnabled(false);
            buttonStop.setEnabled(true);
            buttonPause.setEnabled(true);
            buttonBuy.setEnabled(true);
            buttonSell.setEnabled(true);
            buttonSetPairs.setEnabled(true);
            buttonUpdate.setEnabled(!checkboxTestMode.isSelected());
            buttonCancelLimit.setEnabled(checkBoxLimitedOrders.isSelected());
            buttonShowPlot.setEnabled(true);
            ButtonStatistics.setEnabled(true);
            buttonWebBrowserOpen.setEnabled(true);
            buttonNNBAdd.setEnabled(true);
            buttonNNCAdd.setEnabled(true);
            buttonNNBTrain.setEnabled(true);
            buttonNNCTrain.setEnabled(true);
            buttonRemove.setEnabled(true);
        } catch (Exception e) {
            log("Error: " + e.getMessage(), true, true);
            buttonRun.setEnabled(true);
        }
    }//GEN-LAST:event_buttonRunActionPerformed

    private void buttonStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonStopActionPerformed
        is_paused = false;
        buttonStop.setEnabled(false);
        buttonPause.setEnabled(false);
        pairController.stopPairs();
        buttonRun.setEnabled(true);
        checkboxTestMode.setEnabled(true);
        buttonBuy.setEnabled(false);
        buttonSell.setEnabled(false);
        buttonSetPairs.setEnabled(false);
        buttonUpdate.setEnabled(false);
        buttonCancelLimit.setEnabled(false);
        buttonShowPlot.setEnabled(false);
        ButtonStatistics.setEnabled(false);
        buttonWebBrowserOpen.setEnabled(false);
        buttonNNBAdd.setEnabled(false);
        buttonNNCAdd.setEnabled(false);
        buttonNNBTrain.setEnabled(false);
        buttonNNCTrain.setEnabled(false);
        buttonRemove.setEnabled(false);
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
        pairController.pausePairs(is_paused);
        coinRatingController.doSetPaused(is_paused);
        ButtonStatistics.setEnabled(true);
        buttonWebBrowserOpen.setEnabled(true);
        buttonNNBAdd.setEnabled(true);
        buttonNNCAdd.setEnabled(true);
        buttonNNBTrain.setEnabled(true);
        buttonNNCTrain.setEnabled(true);
        buttonRemove.setEnabled(true);
    }//GEN-LAST:event_buttonPauseActionPerformed

    private void buttonBuyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonBuyActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "BUY");
    }//GEN-LAST:event_buttonBuyActionPerformed

    private void buttonSellActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSellActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "SELL");
    }//GEN-LAST:event_buttonSellActionPerformed

    private void buttonSetPairsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSetPairsActionPerformed
        profitsChecker.setAutoPickStrategyParams(checkBoxAutoStrategyParams.isSelected());
        profitsChecker.setAutoWalkForward(checkBoxWalkForward.isSelected());
        profitsChecker.setAutoStrategies(listBoxAutoStrategies.getSelectedValuesList());
        profitsChecker.setLimitedOrders(checkBoxLimitedOrders.isSelected());
        profitsChecker.setStopGainPercent(checkBoxStopGain.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopGain.getValue()) : null);
        profitsChecker.setStopLossPercent(checkBoxStopLoss.isSelected() ? BigDecimal.valueOf((Integer) spinnerStopLoss.getValue()) : null);
        profitsChecker.setLowHold(checkBoxLowHold.isSelected());
        setPairParams();
        pairController.initBasePairs(textFieldTradePairs.getText());
    }//GEN-LAST:event_buttonSetPairsActionPerformed

    private void buttonUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonUpdateActionPerformed
        if (pairController.getPairs().size() > 0) {
            profitsChecker.updateAllBalances();
        }
    }//GEN-LAST:event_buttonUpdateActionPerformed

    private void buttonCancelLimitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonCancelLimitActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "CANCEL");
    }//GEN-LAST:event_buttonCancelLimitActionPerformed

    private void buttonShowPlotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonShowPlotActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "PLOT");
    }//GEN-LAST:event_buttonShowPlotActionPerformed

    private void checkBoxLimitedOrdersStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxLimitedOrdersStateChanged
        if (profitsChecker != null) {
            profitsChecker.setLimitedOrders(checkBoxLimitedOrders.isSelected());
        }
    }//GEN-LAST:event_checkBoxLimitedOrdersStateChanged

    private void listRatingMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listRatingMouseClicked
        if (evt.getClickCount() == 2) {
            String content = listRating.getSelectedValue();
            if (content != null && !content.isEmpty()) {
                String[] parts = content.split(":");
                if (parts.length > 0) {
                    String newtext = "";
                    if (parts[0].indexOf(')') > 0) {
                        parts = parts[0].split(" ");
                        if (parts.length > 1) {
                            newtext = parts[1].toLowerCase();
                        }
                    } else {
                        newtext = parts[0].toLowerCase();
                    }
                    if (!newtext.isEmpty()) {
                        if (!textFieldTradePairs.getText().isEmpty()) {
                            newtext = textFieldTradePairs.getText() + "," + newtext;
                        }
                        textFieldTradePairs.setText(newtext);
                    }
                }
            }
        }
    }//GEN-LAST:event_listRatingMouseClicked

    private void listCurrenciesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listCurrenciesMouseClicked
        if (evt.getClickCount() == 2) {
            buttonShowPlotActionPerformed(null);
        }
    }//GEN-LAST:event_listCurrenciesMouseClicked

    private void checkboxTestModeStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkboxTestModeStateChanged
        checkBoxLimitedOrders.setEnabled(!checkboxTestMode.isSelected());
        comboBoxLimitedMode.setEnabled(checkBoxLimitedOrders.isSelected() && !checkboxTestMode.isSelected());
    }//GEN-LAST:event_checkboxTestModeStateChanged

    private void checkBoxLowHoldStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkBoxLowHoldStateChanged
        coinRatingController.setLowHold(checkBoxLowHold.isSelected());
    }//GEN-LAST:event_checkBoxLowHoldStateChanged

    private void checkboxAutoOrderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_checkboxAutoOrderStateChanged
        coinRatingController.setAutoOrder(checkboxAutoOrder.isSelected());
    }//GEN-LAST:event_checkboxAutoOrderStateChanged

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        config.Save();
        if (coinRatingController != null && coinRatingController.isAlive()) {
            if (pairController != null) {
                pairController.pausePairs(true);
            }
            coinRatingController.doStop();
            coinRatingController.getSignalOrderController().doStop();
            int i = 0;
            while (++i < 20 && (coinRatingController.isAlive() || coinRatingController.getSignalOrderController().isAlive())) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(mainApplication.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
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

    private void ButtonStatisticsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonStatisticsActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "STATISTICS");
    }//GEN-LAST:event_ButtonStatisticsActionPerformed

    private void buttonWebBrowserOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonWebBrowserOpenActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "BROWSER");
    }//GEN-LAST:event_buttonWebBrowserOpenActionPerformed

    private void checkBoxLimitedOrdersActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxLimitedOrdersActionPerformed
        comboBoxLimitedMode.setEnabled(checkBoxLimitedOrders.isSelected());
    }//GEN-LAST:event_checkBoxLimitedOrdersActionPerformed

    private void comboBoxLimitedModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxLimitedModeActionPerformed
        profitsChecker.setLimitedOrderMode(comboBoxLimitedMode.getSelectedIndex() == 0 ? tradeProfitsController.LimitedOrderMode.LOMODE_SELL : tradeProfitsController.LimitedOrderMode.LOMODE_SELLANDBUY);
    }//GEN-LAST:event_comboBoxLimitedModeActionPerformed

    private void checkboxAutoOrderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkboxAutoOrderActionPerformed
        coinRatingController.setAutoOrder(checkboxAutoOrder.isSelected());
    }//GEN-LAST:event_checkboxAutoOrderActionPerformed

    private void buttonRatingStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonRatingStartActionPerformed
        initAPI();
        coinRatingController.setLowHold(checkBoxLowHold.isSelected());
        coinRatingController.setAutoOrder(checkboxAutoOrder.isSelected());
        coinRatingController.setAutoFastOrder(checkboxAutoFastorder.isSelected());
        coinRatingController.setNoAutoBuysOnDowntrend(checkBoxDowntrendNoAuto.isSelected());
        coinRatingController.getSignalOrderController().setAutoSignalOrder(checkboxAutoSignalOrder.isSelected());
        coinRatingController.getSignalOrderController().setAutoSignalFastOrder(checkboxAutoSignalFastorder.isSelected());
        coinRatingController.setAnalyzer(checkBoxAutoAnalyzer.isSelected());
        coinRatingController.setDelayTime((Integer) spinnerScanRatingDelayTime.getValue());
        coinRatingController.setUpdateTime((Integer) spinnerScanRatingUpdateTime.getValue());
        coinRatingController.setMaxEnter((Integer) spinnerRatingMaxOrders.getValue());
        coinRatingController.setMinRatingForOrder((Integer) spinnerRatingMinForOrder.getValue());
        coinRatingController.getSignalOrderController().setMinSignalRatingForOrder((Integer) spinnerSignalRatingMinForOrder.getValue());
        coinRatingController.getSignalOrderController().getSignalController().setPreloadCount((Integer) spinnerSignalPreloadCount.getValue());
        coinRatingController.getSignalOrderController().setMaxEnter((Integer) spinnerMaxSignalOrders.getValue());
        coinRatingController.setSecondsOrderEnterWait((Integer) spinnerRatingMaxOrderWait.getValue());
        coinRatingController.setProgressBar(progressBarRatingAnalPercent);
        comboBoxRatingSortActionPerformed(null);
        comboBoxRatingSortbyActionPerformed(null);
        coinRatingController.getSignalOrderController().getSignalController().setApiId(textFieldAPIID.getText());
        coinRatingController.getSignalOrderController().getSignalController().setApiHash(textFieldAPIHash.getText());
        coinRatingController.getSignalOrderController().getSignalController().setApiPhone(textFieldPhone.getText());
        coinRatingController.start();
        buttonRatingStart.setEnabled(false);
        buttonRatingCheck.setEnabled(true);
        buttonRatingStop.setEnabled(true);
    }//GEN-LAST:event_buttonRatingStartActionPerformed

    private void buttonRatingStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonRatingStopActionPerformed
        coinRatingController.doStop();
        buttonRatingStart.setEnabled(true);
        buttonRatingCheck.setEnabled(false);
        buttonRatingStop.setEnabled(false);
    }//GEN-LAST:event_buttonRatingStopActionPerformed

    private void comboBoxRatingSortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxRatingSortActionPerformed
        coinRatingController.setSortAsc(comboBoxRatingSort.getSelectedIndex() == 0);
    }//GEN-LAST:event_comboBoxRatingSortActionPerformed

    private void comboBoxRatingSortbyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxRatingSortbyActionPerformed
        int sindex = comboBoxRatingSortby.getSelectedIndex();
        if (sindex < 0) sindex = 0;
        else if (sindex > CoinRatingSort.values().length-1) sindex = CoinRatingSort.values().length-1;
        coinRatingController.setSortby(CoinRatingSort.values()[sindex]);
    }//GEN-LAST:event_comboBoxRatingSortbyActionPerformed

    private void spinnerScanRatingDelayTimeStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerScanRatingDelayTimeStateChanged
        coinRatingController.setDelayTime((Integer) spinnerScanRatingDelayTime.getValue());
    }//GEN-LAST:event_spinnerScanRatingDelayTimeStateChanged

    private void buttonNNBTrainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonNNBTrainActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "NNBTRAIN");
    }//GEN-LAST:event_buttonNNBTrainActionPerformed

    private void buttonNNCTrainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonNNCTrainActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "NNCTRAIN");
    }//GEN-LAST:event_buttonNNCTrainActionPerformed

    private void buttonNNBAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonNNBAddActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "NNBADD");
    }//GEN-LAST:event_buttonNNBAddActionPerformed

    private void buttonNNCAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonNNCAddActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "NNCADD");
    }//GEN-LAST:event_buttonNNCAddActionPerformed

    private void buttonRemoveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonRemoveActionPerformed
        pairController.pairAction(listCurrencies.getSelectedIndex(), "REMOVE");
    }//GEN-LAST:event_buttonRemoveActionPerformed

    private void checkboxAutoFastorderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkboxAutoFastorderActionPerformed
        coinRatingController.setAutoFastOrder(checkboxAutoFastorder.isSelected());
    }//GEN-LAST:event_checkboxAutoFastorderActionPerformed

    private void buttonTlgConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonTlgConnectActionPerformed
        SignalController ccc = new SignalController();
        ccc.setApiId(textFieldAPIID.getText());
        ccc.setApiHash(textFieldAPIHash.getText());
        ccc.setApiPhone(textFieldPhone.getText());
        ccc.startSignalsProcess(true);
    }//GEN-LAST:event_buttonTlgConnectActionPerformed

    private void checkboxAutoSignalOrderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkboxAutoSignalOrderActionPerformed
        coinRatingController.getSignalOrderController().setAutoSignalOrder(checkboxAutoSignalOrder.isSelected());
    }//GEN-LAST:event_checkboxAutoSignalOrderActionPerformed

    private void checkboxAutoSignalFastorderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkboxAutoSignalFastorderActionPerformed
        coinRatingController.getSignalOrderController().setAutoSignalFastOrder(checkboxAutoSignalFastorder.isSelected());
    }//GEN-LAST:event_checkboxAutoSignalFastorderActionPerformed

    private void checkBoxWalkForwardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxWalkForwardActionPerformed
        profitsChecker.setAutoWalkForward(checkBoxWalkForward.isSelected());
    }//GEN-LAST:event_checkBoxWalkForwardActionPerformed

    private void checkBoxAutoStrategyParamsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxAutoStrategyParamsActionPerformed
        profitsChecker.setAutoPickStrategyParams(checkBoxAutoStrategyParams.isSelected());
    }//GEN-LAST:event_checkBoxAutoStrategyParamsActionPerformed

    private void checkBoxDowntrendNoAutoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxDowntrendNoAutoActionPerformed
        coinRatingController.setNoAutoBuysOnDowntrend(checkBoxDowntrendNoAuto.isSelected());
    }//GEN-LAST:event_checkBoxDowntrendNoAutoActionPerformed

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
        java.awt.EventQueue.invokeLater(() -> {
            new mainApplication().setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton ButtonStatistics;
    private javax.swing.JComboBox<String> ComboBoxMainStrategy;
    private javax.swing.JButton buttonBuy;
    private javax.swing.JButton buttonCancelLimit;
    private javax.swing.JButton buttonClear;
    private javax.swing.JButton buttonNNBAdd;
    private javax.swing.JButton buttonNNBTrain;
    private javax.swing.JButton buttonNNCAdd;
    private javax.swing.JButton buttonNNCTrain;
    private javax.swing.JButton buttonPause;
    private javax.swing.JButton buttonRatingCheck;
    private javax.swing.JButton buttonRatingStart;
    private javax.swing.JButton buttonRatingStop;
    private javax.swing.JButton buttonRemove;
    private javax.swing.JButton buttonRun;
    private javax.swing.JButton buttonSell;
    private javax.swing.JButton buttonSetPairs;
    private javax.swing.JButton buttonShowPlot;
    private javax.swing.JButton buttonStop;
    private javax.swing.JButton buttonTlgConnect;
    private javax.swing.JButton buttonUpdate;
    private javax.swing.JButton buttonWebBrowserOpen;
    private javax.swing.JCheckBox checkBoxAutoAnalyzer;
    private javax.swing.JCheckBox checkBoxAutoStrategyParams;
    private javax.swing.JCheckBox checkBoxBuyStopLimited;
    private javax.swing.JCheckBox checkBoxCheckOtherStrategies;
    private javax.swing.JCheckBox checkBoxDowntrendNoAuto;
    private javax.swing.JCheckBox checkBoxLimitedOrders;
    private javax.swing.JCheckBox checkBoxLowHold;
    private javax.swing.JCheckBox checkBoxSellStopLimited;
    private javax.swing.JCheckBox checkBoxStopGain;
    private javax.swing.JCheckBox checkBoxStopLoss;
    private javax.swing.JCheckBox checkBoxWalkForward;
    private javax.swing.JCheckBox checkboxAutoFastorder;
    private javax.swing.JCheckBox checkboxAutoOrder;
    private javax.swing.JCheckBox checkboxAutoSignalFastorder;
    private javax.swing.JCheckBox checkboxAutoSignalOrder;
    private javax.swing.JCheckBox checkboxTestMode;
    private javax.swing.JComboBox<String> comboBoxBarsCount;
    private javax.swing.JComboBox<String> comboBoxBarsInterval;
    private javax.swing.JComboBox<String> comboBoxLimitedMode;
    private javax.swing.JComboBox<String> comboBoxRatingSort;
    private javax.swing.JComboBox<String> comboBoxRatingSortby;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JLabel labelDownTrend;
    private javax.swing.JLabel labelUpTrend;
    private javax.swing.JList<String> listBoxAutoStrategies;
    private javax.swing.JList<String> listCurrencies;
    private javax.swing.JList<String> listProfit;
    private javax.swing.JList<String> listRating;
    private javax.swing.JTextArea logTextarea;
    private javax.swing.JTextArea mainTextarea;
    private javax.swing.JProgressBar progressBarRatingAnalPercent;
    private javax.swing.JSpinner spinnerBuyPercent;
    private javax.swing.JSpinner spinnerBuyStopLimited;
    private javax.swing.JSpinner spinnerMaxSignalOrders;
    private javax.swing.JSpinner spinnerRatingMaxOrderWait;
    private javax.swing.JSpinner spinnerRatingMaxOrders;
    private javax.swing.JSpinner spinnerRatingMinForOrder;
    private javax.swing.JSpinner spinnerScanRatingDelayTime;
    private javax.swing.JSpinner spinnerScanRatingUpdateTime;
    private javax.swing.JSpinner spinnerSellStopLimited;
    private javax.swing.JSpinner spinnerSignalPreloadCount;
    private javax.swing.JSpinner spinnerSignalRatingMinForOrder;
    private javax.swing.JSpinner spinnerStopGain;
    private javax.swing.JSpinner spinnerStopLoss;
    private javax.swing.JSpinner spinnerUpdateDelay;
    private javax.swing.JTextField textFieldAPIHash;
    private javax.swing.JTextField textFieldAPIID;
    private javax.swing.JTextField textFieldApiKey;
    private javax.swing.JTextField textFieldApiSecret;
    private javax.swing.JTextField textFieldPhone;
    private javax.swing.JTextField textFieldTradePairs;
    // End of variables declaration//GEN-END:variables

}
