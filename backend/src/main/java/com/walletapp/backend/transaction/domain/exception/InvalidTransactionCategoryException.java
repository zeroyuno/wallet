package com.walletapp.backend.transaction.domain.exception;

public class InvalidTransactionCategoryException extends RuntimeException {

    public InvalidTransactionCategoryException(String message) {
        super(message);
    }
}
