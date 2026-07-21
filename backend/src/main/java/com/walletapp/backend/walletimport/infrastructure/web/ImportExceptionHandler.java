package com.walletapp.backend.walletimport.infrastructure.web;

import com.walletapp.backend.walletimport.domain.exception.ImportNotFoundException;
import com.walletapp.backend.walletimport.domain.exception.InvalidWalletTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.walletapp.backend.walletimport.infrastructure.web")
class ImportExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ImportExceptionHandler.class);

    @ExceptionHandler(ImportNotFoundException.class)
    ResponseEntity<Map<String, String>> handleNotFound(ImportNotFoundException ex) {
        log.warn("404 {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("400 validation failed: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", details));
    }

    @ExceptionHandler({InvalidWalletTokenException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        log.warn("400 {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
