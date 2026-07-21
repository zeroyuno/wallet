package com.walletapp.backend.walletimport.application;

import com.walletapp.backend.walletimport.application.dto.WalletAccountDto;
import com.walletapp.backend.walletimport.application.dto.WalletCategoryDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordPage;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto hacia la API externa de BudgetBakers Wallet (research.md #1). La implementación real usa
 * HTTP (infrastructure.walletclient); los tests usan un fake en memoria (T011).
 */
public interface WalletImportGateway {

    List<WalletAccountDto> fetchAccounts(String walletApiToken);

    List<WalletCategoryDto> fetchCategories(String walletApiToken);

    // fromDate nullable: sin filtrar desde una fecha (corrida nueva) o reanudando desde el cursor.
    WalletRecordPage fetchRecords(String walletApiToken, LocalDate fromDate, int offset, int limit);
}
