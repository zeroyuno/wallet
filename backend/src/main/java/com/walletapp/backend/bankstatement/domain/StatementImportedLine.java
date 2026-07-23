package com.walletapp.backend.bankstatement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Detalle de depuración de un movimiento importado: en qué columna del documento lo ubicó el modelo
 * y con qué tipo quedó, para poder auditar visualmente sus decisiones (ver research.md #8).
 */
public record StatementImportedLine(LocalDate date, BigDecimal amount, String type, String description,
                                     String columnHeader) {

    public StatementImportedLine {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(columnHeader, "columnHeader must not be null");
    }
}
