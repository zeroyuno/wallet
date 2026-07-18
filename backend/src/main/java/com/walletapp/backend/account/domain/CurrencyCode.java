package com.walletapp.backend.account.domain;

import java.util.regex.Pattern;

/** Código ISO 4217 de 3 letras, ej. "USD". */
public record CurrencyCode(String value) {

    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    public CurrencyCode {
        if (value == null) {
            throw new IllegalArgumentException("Currency code must not be null");
        }
        value = value.trim().toUpperCase();
        if (!ISO_4217.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid currency code: " + value);
        }
    }
}
