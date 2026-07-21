package com.walletapp.backend.walletimport.infrastructure.walletclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Adaptador HTTP real hacia la API de BudgetBakers Wallet (research.md #1 y #8). Forma de la
 * respuesta y filtros verificados contra el OpenAPI oficial y datos reales (T033 de tasks.md):
 * las tres listas (accounts/categories/records) vienen envueltas en un objeto con paginación por
 * offset — no como array plano; `currencyCode` de una cuenta vive dentro de `initialBalance`, no en
 * el nivel raíz; `category` en un movimiento es un objeto anidado, no un id plano; `labels` es un
 * array de objetos (`LabelEmbed`, se usa `.name`); `transfer` no trae un id propio, el id útil es
 * `transfer.mirrorRecord.id` (el movimiento espejo del otro lado de la transferencia).
 * `/v1/api/records` aplica una ventana de 3 meses por defecto si no se manda `recordDate` — se
 * fuerza siempre un filtro `gte.<instant>` explícito para traer el historial completo en una
 * importación nueva. `@JsonIgnoreProperties(ignoreUnknown = true)` en todos los DTOs internos: la
 * respuesta real trae más campos de los que usamos (ej. `balance`, `recordStats`, `accountName`) y
 * no deben romper el parseo. `parentId` de categoría no verificado con un ejemplo de subcategoría
 * propio, pero confirmado por el OpenAPI — los tests automatizados usan un fake
 * (FakeWalletImportGateway) para no depender de red externa.
 */
@Component
class WalletApiHttpClient implements WalletImportGateway {

    private static final String BASE_URL = "https://rest.budgetbakers.com/wallet";
    private static final int LIST_PAGE_SIZE = 100;
    // Suficientemente atrás como para cubrir cualquier historial real; usado como cota inferior
    // explícita del filtro recordDate cuando no hay cursor todavía (importación nueva).
    private static final LocalDate EARLIEST_RECORD_DATE = LocalDate.of(2000, 1, 1);

    private final RestClient restClient;

    WalletApiHttpClient() {
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    @Override
    public List<WalletAccountDto> fetchAccounts(String walletApiToken) {
        List<WalletAccountDto> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            int currentOffset = offset;
            WalletApiAccountsResponse page = execute(() -> restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/api/accounts")
                            .queryParam("offset", currentOffset).queryParam("limit", LIST_PAGE_SIZE).build())
                    .header("Authorization", "Bearer " + walletApiToken)
                    .retrieve()
                    .body(WalletApiAccountsResponse.class));
            List<WalletApiAccount> accounts = page == null || page.accounts() == null ? List.of() : page.accounts();
            accounts.forEach(a -> all.add(toDto(a)));
            if (accounts.size() < LIST_PAGE_SIZE) {
                break;
            }
            offset += LIST_PAGE_SIZE;
        }
        return all;
    }

    @Override
    public List<WalletCategoryDto> fetchCategories(String walletApiToken) {
        List<WalletCategoryDto> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            int currentOffset = offset;
            WalletApiCategoriesResponse page = execute(() -> restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/api/categories")
                            .queryParam("offset", currentOffset).queryParam("limit", LIST_PAGE_SIZE).build())
                    .header("Authorization", "Bearer " + walletApiToken)
                    .retrieve()
                    .body(WalletApiCategoriesResponse.class));
            List<WalletApiCategory> categories = page == null || page.categories() == null ? List.of()
                    : page.categories();
            categories.forEach(c -> all.add(toDto(c)));
            if (categories.size() < LIST_PAGE_SIZE) {
                break;
            }
            offset += LIST_PAGE_SIZE;
        }
        return all;
    }

    @Override
    public WalletRecordPage fetchRecords(String walletApiToken, LocalDate fromDate, int offset, int limit) {
        LocalDate effectiveFrom = fromDate == null ? EARLIEST_RECORD_DATE : fromDate;
        String recordDateFilter = "gte." + effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        WalletApiRecordsResponse page = execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/api/records")
                        .queryParam("offset", offset).queryParam("limit", limit)
                        .queryParam("recordDate", recordDateFilter)
                        .build())
                .header("Authorization", "Bearer " + walletApiToken)
                .retrieve()
                .body(WalletApiRecordsResponse.class));
        List<WalletApiRecord> records = page == null || page.records() == null ? List.of() : page.records();
        List<WalletRecordDto> dtos = records.stream().map(WalletApiHttpClient::toDto).toList();
        // Heurística estándar de paginación por offset: si la página vino completa, puede haber más.
        boolean hasMore = records.size() == limit;
        return new WalletRecordPage(dtos, hasMore);
    }

    private static <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new InvalidWalletTokenException("Invalid or expired Wallet API token");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RateLimitExceededException("Wallet API rate limit exceeded");
        }
    }

    private static WalletAccountDto toDto(WalletApiAccount account) {
        BigDecimal balance = BigDecimal.ZERO;
        String currencyCode = null;
        if (account.initialBalance() != null) {
            balance = account.initialBalance().value();
            currencyCode = account.initialBalance().currencyCode();
        }
        return new WalletAccountDto(account.id(), account.name(), account.accountType(), currencyCode, balance);
    }

    private static WalletCategoryDto toDto(WalletApiCategory category) {
        String groupId = category.group() == null ? null : category.group().id();
        return new WalletCategoryDto(category.id(), category.name(), category.parentId(), groupId);
    }

    private static WalletRecordDto toDto(WalletApiRecord record) {
        // Wallet manda expense en negativo e income en positivo; nuestro dominio siempre exige
        // magnitud positiva y expresa la dirección solo con `recordType` (verificado con datos
        // reales: T033) — sin este abs() todo gasto importado fallaría con "amount must be greater
        // than zero".
        BigDecimal amount = record.amount() == null ? BigDecimal.ZERO : record.amount().value().abs();
        String categoryId = record.category() == null ? null : record.category().id();
        String transferId = (record.transfer() == null || record.transfer().mirrorRecord() == null) ? null
                : record.transfer().mirrorRecord().id();
        List<String> labels = record.labels() == null ? List.of()
                : Arrays.stream(record.labels()).map(WalletApiLabel::name).toList();
        return new WalletRecordDto(record.id(), record.accountId(), amount, record.recordDate(),
                record.recordType(), categoryId, record.counterParty(), record.note(),
                record.paymentType(), record.recordState(), transferId, labels);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiMoney(BigDecimal value, String currencyCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiAccount(String id, String name, String accountType, WalletApiMoney initialBalance) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiAccountsResponse(List<WalletApiAccount> accounts, Integer limit, Integer nextOffset,
                                              Integer offset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiGroup(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiCategory(String id, String name, String parentId, WalletApiGroup group) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiCategoriesResponse(List<WalletApiCategory> categories, Integer limit, Integer offset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiCategoryRef(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiLabel(String id, String name, String color, Boolean archived) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiMirrorRecord(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiTransfer(String type, WalletApiMirrorRecord mirrorRecord) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiRecord(String id, String accountId, WalletApiMoney amount, LocalDate recordDate,
                                    String recordType, WalletApiCategoryRef category, String counterParty,
                                    String note, String paymentType, String recordState,
                                    WalletApiTransfer transfer, WalletApiLabel[] labels) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletApiRecordsResponse(List<WalletApiRecord> records, Integer limit, Integer offset) {
    }
}
