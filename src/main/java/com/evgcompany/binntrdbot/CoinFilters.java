/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 *
 * @author EVG_Adminer
 */
public class CoinFilters implements java.io.Serializable {
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    
    private boolean filterPrice = false;
    private BigDecimal filterPriceTickSize = BigDecimal.ZERO;
    private BigDecimal filterMinPrice = BigDecimal.ZERO;
    private BigDecimal filterMaxPrice = BigDecimal.ZERO;
    private boolean filterQty = false;
    private BigDecimal filterQtyStep = BigDecimal.ZERO;
    private BigDecimal filterMinQty = BigDecimal.ZERO;
    private BigDecimal filterMaxQty = BigDecimal.ZERO;
    private boolean filterNotional = false;
    private BigDecimal filterMinNotional = BigDecimal.ZERO;

    private BigDecimal currentPrice = null;
    private BigDecimal currentAmount = null;
    
    private void do_init(String baseAsset, String quoteAsset, List<SymbolFilter> filters) {
        baseAssetSymbol = baseAsset;
        quoteAssetSymbol = quoteAsset;
        filters.forEach((filter)->{
            if (null != filter.getFilterType()) switch (filter.getFilterType()) {
                case PRICE_FILTER:
                    filterPrice = true;
                    filterMinPrice = new BigDecimal(filter.getMinPrice());
                    filterMaxPrice = new BigDecimal(filter.getMaxPrice());
                    filterPriceTickSize = new BigDecimal(filter.getTickSize());
                    break;
                case LOT_SIZE:
                    filterQty = true;
                    filterQtyStep = new BigDecimal(filter.getStepSize());
                    filterMinQty = new BigDecimal(filter.getMinQty());
                    filterMaxQty = new BigDecimal(filter.getMaxQty());
                    break;
                case MIN_NOTIONAL:
                    filterNotional = true;
                    filterMinNotional = new BigDecimal(filter.getMinNotional());
                    break;
                default:
                    break;
            }
        });
    }
    
    public CoinFilters(String baseAsset, String quoteAsset, List<SymbolFilter> filters) {
        do_init(baseAsset, quoteAsset, filters);
    }
    
    public CoinFilters(String symbol, TradingAPIAbstractInterface rclient) {
        ExchangeInfo info = rclient.getExchangeInfo();
        SymbolInfo pair_sinfo = info.getSymbolInfo(symbol);
        do_init(pair_sinfo.getBaseAsset(), pair_sinfo.getQuoteAsset(), pair_sinfo.getFilters());
    }

    public BigDecimal normalizeQuantity(BigDecimal qty, boolean qty_down_only) {
        if (filterQty) {
            BigDecimal pqty = qty;
            if (filterQtyStep.compareTo(BigDecimal.ZERO) > 0) {
                qty = qty.divide(filterQtyStep).setScale(0, RoundingMode.HALF_UP).multiply(filterQtyStep);
            }
            if (qty.compareTo(filterMinQty) < 0) {
                qty = filterMinQty;
            } else if (qty.compareTo(filterMaxQty) > 0) {
                qty = filterMaxQty;
            }
            if (qty_down_only && qty.compareTo(pqty) > 0) {
                qty = qty.subtract(filterQtyStep);
                if (qty.compareTo(BigDecimal.ZERO) < 0) {
                    qty = BigDecimal.ZERO;
                }
            }
        }
        return qty;
    }
    public BigDecimal normalizePrice(BigDecimal price) {
        if (filterPrice) {
            if (filterPriceTickSize.compareTo(BigDecimal.ZERO) > 0) {
                price = price.divide(filterPriceTickSize).setScale(0, RoundingMode.HALF_UP).multiply(filterPriceTickSize);
            }
            if (price.compareTo(filterMinPrice) < 0) {
                price = filterMinPrice;
            } else if (price.compareTo(filterMaxPrice) > 0) {
                price = filterMaxPrice;
            }
        }
        return price;
    }
    public BigDecimal normalizeNotionalQuantity(BigDecimal quantity, BigDecimal price) {
        if (filterNotional && price.compareTo(BigDecimal.ZERO) > 0) {
            if (quantity.multiply(price).compareTo(filterMinNotional) < 0) {
                quantity = filterMinNotional.divide(price, filterMinNotional.scale(), RoundingMode.HALF_UP);
                quantity = normalizeQuantity(quantity, false);
            }
        }
        return quantity;
    }

    /**
     * @return the baseAssetSymbol
     */
    public String getBaseAssetSymbol() {
        return baseAssetSymbol;
    }

    /**
     * @return the quoteAssetSymbol
     */
    public String getQuoteAssetSymbol() {
        return quoteAssetSymbol;
    }

    /**
     * @return the filterPrice
     */
    public boolean isFilterPrice() {
        return filterPrice;
    }

    /**
     * @return the filterPriceTickSize
     */
    public BigDecimal getFilterPriceTickSize() {
        return filterPriceTickSize;
    }

    /**
     * @return the filterMinPrice
     */
    public BigDecimal getFilterMinPrice() {
        return filterMinPrice;
    }

    /**
     * @return the filterMaxPrice
     */
    public BigDecimal getFilterMaxPrice() {
        return filterMaxPrice;
    }

    /**
     * @return the filterQty
     */
    public boolean isFilterQty() {
        return filterQty;
    }

    /**
     * @return the filterQtyStep
     */
    public BigDecimal getFilterQtyStep() {
        return filterQtyStep;
    }

    /**
     * @return the filterMinQty
     */
    public BigDecimal getFilterMinQty() {
        return filterMinQty;
    }

    /**
     * @return the filterMaxQty
     */
    public BigDecimal getFilterMaxQty() {
        return filterMaxQty;
    }

    /**
     * @return the filterNotional
     */
    public boolean isFilterNotional() {
        return filterNotional;
    }

    /**
     * @return the filterMinNotional
     */
    public BigDecimal getFilterMinNotional() {
        return filterMinNotional;
    }

    public void logFiltersInfo() {
        mainApplication.getInstance().log(baseAssetSymbol+quoteAssetSymbol + " filters:");
        if (filterPrice) {
            mainApplication.getInstance().log("Price: min="+NumberFormatter.df8.format(filterMinPrice)+"; max="+NumberFormatter.df8.format(filterMaxPrice)+"; tick=" + NumberFormatter.df8.format(filterPriceTickSize));
        }
        if (filterQty) {
            mainApplication.getInstance().log("Quantity: min="+NumberFormatter.df8.format(filterMinQty)+"; max="+NumberFormatter.df8.format(filterMaxQty)+"; step=" + NumberFormatter.df8.format(filterQtyStep));
        }
        if (filterNotional) {
            mainApplication.getInstance().log("Notional: " + NumberFormatter.df8.format(filterMinNotional));
        }
        mainApplication.getInstance().log("");
    }

    public void prepareForBuy(OrdersController ordersController) {
        BigDecimal tobuy_price = normalizePrice(currentPrice);
        BigDecimal tobuy_amount = currentAmount;
        tobuy_amount = normalizeQuantity(tobuy_amount.divide(tobuy_price, RoundingMode.HALF_DOWN), true);
        tobuy_amount = normalizeNotionalQuantity(tobuy_amount, tobuy_price);
        if (!BalanceController.getInstance().canBuy(baseAssetSymbol+quoteAssetSymbol, tobuy_amount, tobuy_price) && filterQtyStep != null && tobuy_amount.compareTo(filterQtyStep) > 0) {
            tobuy_amount = tobuy_amount.subtract(filterQtyStep);
        }
        currentPrice = tobuy_price;
        currentAmount = tobuy_amount;
    }
    
    public void prepareForSell(OrdersController ordersController) {
        BigDecimal tosell_price = normalizePrice(currentPrice);
        BigDecimal tosell_amount = normalizeQuantity(currentAmount, true);
        tosell_amount = normalizeNotionalQuantity(tosell_amount, tosell_price);
        if (!BalanceController.getInstance().canSell(baseAssetSymbol+quoteAssetSymbol, tosell_amount) && filterQtyStep != null && tosell_amount.compareTo(filterQtyStep) > 0) {
            tosell_amount = tosell_amount.subtract(filterQtyStep);
        }
        currentPrice = tosell_price;
        currentAmount = tosell_amount;
    }
    
    /**
     * @return the currentPrice
     */
    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    /**
     * @param currentPrice the currentPrice to set
     */
    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    /**
     * @return the currentAmount
     */
    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    /**
     * @param currentAmount the currentAmount to set
     */
    public void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = currentAmount;
    }
}
