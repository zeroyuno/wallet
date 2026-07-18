package com.walletapp.backend.account.domain.exception;

public class InvalidCategoryHierarchyException extends RuntimeException {

    public InvalidCategoryHierarchyException(String message) {
        super(message);
    }
}
