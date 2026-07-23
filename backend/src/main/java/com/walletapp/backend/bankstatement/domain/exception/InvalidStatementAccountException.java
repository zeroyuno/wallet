package com.walletapp.backend.bankstatement.domain.exception;

public class InvalidStatementAccountException extends RuntimeException {
    public InvalidStatementAccountException(String message) {
        super(message);
    }
}
