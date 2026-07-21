package com.walletapp.backend.walletimport.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Todos los campos de Record menos photos y place (decisión explícita del usuario: no se guardan
// adjuntos ni ubicación). transferId y labels se guardan tal cual vienen de Wallet, sin intentar
// modelar la relación de transferencia ni las etiquetas como conceptos propios de esta app todavía.
public record WalletRecordDto(String id, String accountId, BigDecimal amount, LocalDate recordDate,
                               String recordType, String categoryId, String counterParty, String note,
                               String paymentType, String recordState, String transferId, List<String> labels) {
}
