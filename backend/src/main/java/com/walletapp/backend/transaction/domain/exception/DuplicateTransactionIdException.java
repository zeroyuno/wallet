package com.walletapp.backend.transaction.domain.exception;

public class DuplicateTransactionIdException extends RuntimeException {

    public DuplicateTransactionIdException(String message) {
        super(message);
    }
}
