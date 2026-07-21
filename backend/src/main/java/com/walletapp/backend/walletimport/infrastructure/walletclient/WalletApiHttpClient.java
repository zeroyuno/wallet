package com.walletapp.backend.walletimport.infrastructure.walletclient;

import com.walletapp.backend.walletimport.application.WalletImportGateway;
import com.walletapp.backend.walletimport.application.dto.WalletAccountDto;
import com.walletapp.backend.walletimport.application.dto.WalletCategoryDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordPage;
import com.walletapp.backend.walletimport.domain.exception.InvalidWalletTokenException;
import com.walletapp.backend.walletimport.domain.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Adaptador HTTP real hacia la API de BudgetBakers Wallet (research.md #1). Los nombres de
 * endpoint y campos siguen el OpenAPI publicado en https://rest.budgetbakers.com/wallet/openapi;
 * la forma exacta de la respuesta (envoltorios, paginación) se valida contra la API real con un
 * token de prueba en T033 (quickstart.md) — los tests automatizados usan un fake (ver
 * walletimport.application, FakeWalletImportGateway en tests) para no depender de red externa.
 */
@Component
class WalletApiHttpClient implements WalletImportGateway {

    private static final String BASE_URL = "https://rest.budgetbakers.com/wallet";

    private final RestClient restClient;

    WalletApiHttpClient() {
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    @Override
    public List<WalletAccountDto> fetchAccounts(String walletApiToken) {
        WalletApiAccount[] accounts = execute(() -> restClient.get()
                .uri("/v1/api/accounts")
                .header("Authorization", "Bearer " + walletApiToken)
                .retrieve()
                .body(WalletApiAccount[].class));
        return accounts == null ? List.of() : Arrays.stream(accounts).map(WalletApiHttpClient::toDto).toList();
    }

    @Override
    public List<WalletCategoryDto> fetchCategories(String walletApiToken) {
        WalletApiCategory[] categories = execute(() -> restClient.get()
                .uri("/v1/api/categories")
                .header("Authorization", "Bearer " + walletApiToken)
                .retrieve()
                .body(WalletApiCategory[].class));
        return categories == null ? List.of() : Arrays.stream(categories).map(WalletApiHttpClient::toDto).toList();
    }

    @Override
    public WalletRecordPage fetchRecords(String walletApiToken, LocalDate fromDate, int offset, int limit) {
        WalletApiRecord[] records = execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v1/api/records").queryParam("offset", offset).queryParam("limit", limit);
                    if (fromDate != null) {
                        uriBuilder.queryParam("fromDate", fromDate);
                    }
                    return uriBuilder.build();
                })
                .header("Authorization", "Bearer " + walletApiToken)
                .retrieve()
                .body(WalletApiRecord[].class));
        List<WalletRecordDto> dtos = records == null ? List.of()
                : Arrays.stream(records).map(WalletApiHttpClient::toDto).toList();
        // Heurística estándar de paginación por offset: si la página vino completa, puede haber más.
        boolean hasMore = records != null && records.length == limit;
        return new WalletRecordPage(dtos, hasMore);
    }

    private static <T> T execute(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new InvalidWalletTokenException("Invalid or expired Wallet API token");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RateLimitExceededException("Wallet API rate limit exceeded");
        }
    }

    private static WalletAccountDto toDto(WalletApiAccount account) {
        BigDecimal balance = account.initialBalance() == null ? BigDecimal.ZERO : account.initialBalance().value();
        return new WalletAccountDto(account.id(), account.name(), account.accountType(), account.currencyCode(),
                balance);
    }

    private static WalletCategoryDto toDto(WalletApiCategory category) {
        String groupId = category.group() == null ? null : category.group().id();
        return new WalletCategoryDto(category.id(), category.name(), category.parentId(), groupId);
    }

    private static WalletRecordDto toDto(WalletApiRecord record) {
        BigDecimal amount = record.amount() == null ? BigDecimal.ZERO : record.amount().value();
        return new WalletRecordDto(record.id(), record.accountId(), amount, record.recordDate(),
                record.recordType(), record.categoryId(), record.counterParty(), record.note());
    }

    private record WalletApiMoney(BigDecimal value, String currencyCode) {
    }

    private record WalletApiAccount(String id, String name, String accountType, String currencyCode,
                                     WalletApiMoney initialBalance) {
    }

    private record WalletApiGroup(String id) {
    }

    private record WalletApiCategory(String id, String name, String parentId, WalletApiGroup group) {
    }

    private record WalletApiRecord(String id, String accountId, WalletApiMoney amount, LocalDate recordDate,
                                    String recordType, String categoryId, String counterParty, String note) {
    }
}
