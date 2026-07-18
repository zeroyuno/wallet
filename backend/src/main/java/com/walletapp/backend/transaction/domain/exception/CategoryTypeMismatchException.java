package com.walletapp.backend.transaction.domain.exception;

public class CategoryTypeMismatchException extends RuntimeException {

    public CategoryTypeMismatchException(String message) {
        super(message);
    }
}
