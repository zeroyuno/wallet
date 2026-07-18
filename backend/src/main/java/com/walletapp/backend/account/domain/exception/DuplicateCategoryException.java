package com.walletapp.backend.account.domain.exception;

public class DuplicateCategoryException extends RuntimeException {

    public DuplicateCategoryException(String message) {
        super(message);
    }
}
