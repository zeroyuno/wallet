package com.walletapp.backend.bankstatement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Embeddable
public class StatementImportedLineEmbeddable {

    @Column(name = "line_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String description;

    @Column(name = "column_header", nullable = false)
    private String columnHeader;

    protected StatementImportedLineEmbeddable() {
        // JPA
    }

    public StatementImportedLineEmbeddable(LocalDate date, BigDecimal amount, String type, String description,
                                            String columnHeader) {
        this.date = date;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.columnHeader = columnHeader;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getColumnHeader() {
        return columnHeader;
    }
}
