package com.chubb.reporting.fx;

import com.chubb.reporting.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Static indicative rates from {@code reporting.fx.rates-to-usd} (value of 1 unit of the currency in USD). */
@Component
@ConfigurationProperties(prefix = "reporting.fx")
public class ConfiguredFxRateProvider implements FxRateProvider {

    private Map<String, BigDecimal> ratesToUsd = Map.of();

    public void setRatesToUsd(Map<String, BigDecimal> ratesToUsd) {
        this.ratesToUsd = ratesToUsd;
    }

    @Override
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (from.equals(to)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(rate(from)).divide(rate(to), MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Set<String> supportedCurrencies() {
        return Set.copyOf(ratesToUsd.keySet());
    }

    @Override
    public String source() {
        return "Static indicative rates from configuration (reporting.fx.rates-to-usd); not live market rates";
    }

    private BigDecimal rate(String currency) {
        BigDecimal rate = ratesToUsd.get(currency);
        if (rate == null) {
            throw new UnsupportedCurrencyException(currency);
        }
        return rate;
    }
}
