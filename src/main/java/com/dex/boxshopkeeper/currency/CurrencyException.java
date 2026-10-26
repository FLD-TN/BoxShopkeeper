package com.dex.boxshopkeeper.currency;

/** A currency plugin refused or failed a withdrawal; nothing was removed. */
public class CurrencyException extends RuntimeException {

    public CurrencyException(String message) {
        super(message);
    }

    public CurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
