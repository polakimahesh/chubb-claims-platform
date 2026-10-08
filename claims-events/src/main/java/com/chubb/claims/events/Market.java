package com.chubb.claims.events;

/**
 * Markets served by the platform (assumption: six APAC markets, see docs/decisions-and-assumptions.md).
 * Each market settles in one currency; claims must be lodged in their market's currency.
 */
public enum Market {
    SG("SGD"), HK("HKD"), MY("MYR"), TH("THB"), ID("IDR"), AU("AUD");

    private final String currency;

    Market(String currency) {
        this.currency = currency;
    }

    public String currency() {
        return currency;
    }
}
