package com.walletapp.backend.walletimport.application;

import com.walletapp.backend.walletimport.application.dto.WalletAccountDto;
import com.walletapp.backend.walletimport.application.dto.WalletCategoryDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordPage;
import com.walletapp.backend.walletimport.domain.exception.InvalidWalletTokenException;
import com.walletapp.backend.walletimport.domain.exception.RateLimitExceededException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fake en memoria de la API de Wallet, usado en tests para no depender de red externa (T011). */
public class FakeWalletImportGateway implements WalletImportGateway {

    private final List<WalletAccountDto> accounts = new ArrayList<>();
    private final List<WalletCategoryDto> categories = new ArrayList<>();
    private final List<WalletRecordDto> records = new ArrayList<>();
    private String invalidToken;
    private int rateLimitAfterCalls = -1;
    private int callCount;

    public FakeWalletImportGateway withAccounts(WalletAccountDto... accounts) {
        this.accounts.addAll(List.of(accounts));
        return this;
    }

    public FakeWalletImportGateway withCategories(WalletCategoryDto... categories) {
        this.categories.addAll(List.of(categories));
        return this;
    }

    public FakeWalletImportGateway withRecords(WalletRecordDto... records) {
        this.records.addAll(List.of(records));
        return this;
    }

    public FakeWalletImportGateway rejectingToken(String token) {
        this.invalidToken = token;
        return this;
    }

    /** A partir de la próxima llamada número {@code n} (1-indexado), lanza RateLimitExceededException. */
    public FakeWalletImportGateway rateLimitedAfter(int n) {
        this.rateLimitAfterCalls = n;
        return this;
    }

    @Override
    public List<WalletAccountDto> fetchAccounts(String walletApiToken) {
        beforeCall(walletApiToken);
        return List.copyOf(accounts);
    }

    @Override
    public List<WalletCategoryDto> fetchCategories(String walletApiToken) {
        beforeCall(walletApiToken);
        return List.copyOf(categories);
    }

    @Override
    public WalletRecordPage fetchRecords(String walletApiToken, LocalDate fromDate, int offset, int limit) {
        beforeCall(walletApiToken);
        List<WalletRecordDto> filtered = fromDate == null ? records
                : records.stream().filter(r -> !r.recordDate().isBefore(fromDate)).toList();
        if (offset >= filtered.size()) {
            return new WalletRecordPage(List.of(), false);
        }
        int end = Math.min(offset + limit, filtered.size());
        return new WalletRecordPage(filtered.subList(offset, end), end < filtered.size());
    }

    private void beforeCall(String walletApiToken) {
        callCount++;
        if (invalidToken != null && invalidToken.equals(walletApiToken)) {
            throw new InvalidWalletTokenException("Invalid Wallet API token");
        }
        if (rateLimitAfterCalls >= 0 && callCount > rateLimitAfterCalls) {
            throw new RateLimitExceededException("Simulated Wallet API rate limit");
        }
    }
}
