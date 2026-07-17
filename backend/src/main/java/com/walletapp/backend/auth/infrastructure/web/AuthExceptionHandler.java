package com.walletapp.backend.auth.infrastructure.web;

import com.walletapp.backend.auth.domain.exception.AccountLockedException;
import com.walletapp.backend.auth.domain.exception.EmailAlreadyInUseException;
import com.walletapp.backend.auth.domain.exception.InvalidCredentialsException;
import com.walletapp.backend.auth.domain.exception.InvalidPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.walletapp.backend.auth.infrastructure.web")
class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ResponseEntity<Map<String, String>> handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({InvalidPasswordException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<Map<String, String>> handleAccountLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", ex.getMessage()));
    }
}
