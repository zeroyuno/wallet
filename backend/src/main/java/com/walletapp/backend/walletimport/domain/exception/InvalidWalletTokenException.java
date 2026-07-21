package com.walletapp.backend.walletimport.domain.exception;

public class InvalidWalletTokenException extends RuntimeException {
    public InvalidWalletTokenException(String message) {
        super(message);
    }
}
