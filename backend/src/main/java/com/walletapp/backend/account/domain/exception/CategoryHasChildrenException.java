package com.walletapp.backend.account.domain.exception;

public class CategoryHasChildrenException extends RuntimeException {

    public CategoryHasChildrenException(String message) {
        super(message);
    }
}
