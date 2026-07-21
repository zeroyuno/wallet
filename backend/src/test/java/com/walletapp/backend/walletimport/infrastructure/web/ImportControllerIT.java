package com.walletapp.backend.walletimport.infrastructure.web;

import com.walletapp.backend.walletimport.application.FakeWalletImportGateway;
import com.walletapp.backend.walletimport.application.WalletImportGateway;
import com.walletapp.backend.walletimport.application.dto.WalletAccountDto;
import com.walletapp.backend.walletimport.application.dto.WalletCategoryDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ImportControllerIT {

    private static final String REJECTED_TOKEN = "invalid-wallet-token";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        WalletImportGateway walletImportGateway() {
            return new FakeWalletImportGateway()
                    .rejectingToken(REJECTED_TOKEN)
                    .withAccounts(new WalletAccountDto("acc-1", "Efectivo Wallet", "Cash", "USD",
                            new BigDecimal("100")))
                    .withCategories(new WalletCategoryDto("cat-1", "Comida", null, null))
                    .withRecords(new WalletRecordDto("rec-1", "acc-1", new BigDecimal("30"), LocalDate.now(),
                            "EXPENSE", "cat-1", "Supermercado", null, "CARD", "CONFIRMED", null,
                            java.util.List.of("mercado")));
        }
    }

    @Autowired
    MockMvc mockMvc;

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"" + email + "\",\"password\":\"password123\",\"displayName\":\"Test\"}"));

        String loginResponse = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(loginResponse, "$.accessToken");
    }

    private String startImport(String token, String walletToken) throws Exception {
        String response = mockMvc.perform(post("/api/imports").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletApiToken\":\"" + walletToken + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private String waitForCompletion(String token, String importId) throws Exception {
        String status = "IN_PROGRESS";
        for (int i = 0; i < 50 && "IN_PROGRESS".equals(status); i++) {
            Thread.sleep(100);
            String response = mockMvc.perform(get("/api/imports/" + importId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            status = com.jayway.jsonpath.JsonPath.read(response, "$.status");
        }
        return status;
    }

    @Test
    void importsAccountsCategoriesAndTransactions() throws Exception {
        String token = registerAndLogin("import-full@example.com");

        String importId = startImport(token, "valid-token");
        String finalStatus = waitForCompletion(token, importId);

        assertThat(finalStatus).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/imports/" + importId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.accountsImported").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.categoriesImported").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.transactionsImported").value(1));

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsInvalidWalletTokenWithoutCreatingAnything() throws Exception {
        String token = registerAndLogin("import-invalid-token@example.com");

        mockMvc.perform(post("/api/imports").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletApiToken\":\"" + REJECTED_TOKEN + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(0));
    }

    // FR-006, SC-003: correr la importación dos veces no duplica cuentas/categorías/movimientos.
    @Test
    void reRunningTheImportDoesNotDuplicateData() throws Exception {
        String token = registerAndLogin("import-rerun@example.com");

        String firstImportId = startImport(token, "valid-token");
        waitForCompletion(token, firstImportId);

        String secondImportId = startImport(token, "valid-token");
        waitForCompletion(token, secondImportId);

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(1));
    }

    // FR-010: una importación ajena responde 404, no 403 (mismo criterio ya usado en el resto de la API).
    @Test
    void importsAreIsolatedBetweenUsers() throws Exception {
        String ownerToken = registerAndLogin("import-iso-a@example.com");
        String otherToken = registerAndLogin("import-iso-b@example.com");

        String importId = startImport(ownerToken, "valid-token");

        mockMvc.perform(get("/api/imports/" + importId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/imports/" + java.util.UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
