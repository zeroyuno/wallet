package com.walletapp.backend.bankstatement.infrastructure.web;

import com.walletapp.backend.bankstatement.domain.exception.InvalidStatementAccountException;
import com.walletapp.backend.bankstatement.domain.exception.StatementImportNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.UncheckedIOException;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.walletapp.backend.bankstatement.infrastructure.web")
class StatementImportExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StatementImportExceptionHandler.class);

    // FR-002: cuenta ajena o inexistente -> 404 (no 403, mismo criterio ya usado en el resto de la API).
    @ExceptionHandler({InvalidStatementAccountException.class, StatementImportNotFoundException.class})
    ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        log.warn("404 {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({UncheckedIOException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        log.warn("400 {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
