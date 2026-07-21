package com.walletapp.backend.walletimport.domain.exception;

public class ImportNotFoundException extends RuntimeException {
    public ImportNotFoundException(String message) {
        super(message);
    }
}
