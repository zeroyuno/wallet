package com.walletapp.backend.transaction.domain.exception;

public class InvalidTransactionAccountException extends RuntimeException {

    public InvalidTransactionAccountException(String message) {
        super(message);
    }
}
