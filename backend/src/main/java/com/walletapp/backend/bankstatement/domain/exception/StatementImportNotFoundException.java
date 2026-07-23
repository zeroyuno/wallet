package com.walletapp.backend.bankstatement.domain.exception;

public class StatementImportNotFoundException extends RuntimeException {
    public StatementImportNotFoundException(String message) {
        super(message);
    }
}
