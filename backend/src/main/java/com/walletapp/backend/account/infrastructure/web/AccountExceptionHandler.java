package com.walletapp.backend.account.infrastructure.web;

import com.walletapp.backend.account.domain.exception.AccountNotFoundException;
import com.walletapp.backend.account.domain.exception.CategoryHasChildrenException;
import com.walletapp.backend.account.domain.exception.CategoryNotFoundException;
import com.walletapp.backend.account.domain.exception.DuplicateCategoryException;
import com.walletapp.backend.account.domain.exception.InvalidCategoryHierarchyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.walletapp.backend.account.infrastructure.web")
class AccountExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountExceptionHandler.class);

    @ExceptionHandler({AccountNotFoundException.class, CategoryNotFoundException.class})
    ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        log.warn("404 {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    ResponseEntity<Map<String, String>> handleDuplicateCategory(DuplicateCategoryException ex) {
        log.warn("409 {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CategoryHasChildrenException.class)
    ResponseEntity<Map<String, String>> handleCategoryHasChildren(CategoryHasChildrenException ex) {
        log.warn("409 {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // FR-010 (feature 003): no se puede eliminar una cuenta o categoría con transacciones asociadas.
    // account no depende de transaction (principio II) — el bloqueo lo garantiza la FK "ON DELETE
    // RESTRICT" de transactions.account_id/category_id; acá solo se traduce esa violación a 409 en
    // vez de dejarla propagar sin manejar (ver research.md de la feature 003).
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("409 no se puede eliminar: existen registros asociados");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "No se puede eliminar: existen registros asociados"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("400 validation failed: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", details));
    }

    @ExceptionHandler({InvalidCategoryHierarchyException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        log.warn("400 {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
