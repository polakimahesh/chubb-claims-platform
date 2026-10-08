package com.chubb.reporting.fx;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Currency conversion for cross-market exposure totals. The shipped implementation reads indicative static rates
 * from configuration; a production implementation would call a treasury/market-data service behind this interface.
 */
public interface FxRateProvider {

    /** Converts {@code amount} from one currency to another, rounded to 2 decimals. */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency);

    Set<String> supportedCurrencies();

    /** Human-readable description of where the rates come from (returned with reports so nobody mistakes them for live rates). */
    String source();
}
