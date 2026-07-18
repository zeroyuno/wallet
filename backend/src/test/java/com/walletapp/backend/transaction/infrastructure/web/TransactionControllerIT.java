package com.walletapp.backend.transaction.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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

    private String createAccount(String token, double initialBalance) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Efectivo\",\"type\":\"CASH\",\"currency\":\"USD\",\"initialBalance\":"
                                + initialBalance + "}"))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private String createCategory(String token, String name, String type) throws Exception {
        String response = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    @Test
    void createsTransactionAndUpdatesBalance() throws Exception {
        String token = registerAndLogin("tx-owner@example.com");
        String accountId = createAccount(token, 100);
        String categoryId = createCategory(token, "Comida", "EXPENSE");

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\",\"categoryId\":\"" + categoryId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(30));

        mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/accounts/" + accountId + "/balance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70));
    }

    @Test
    void rejectsTransactionOnForeignAccount() throws Exception {
        String ownerToken = registerAndLogin("tx-a@example.com");
        String otherToken = registerAndLogin("tx-b@example.com");
        String accountId = createAccount(ownerToken, 100);

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsTransactionWithMismatchedCategoryType() throws Exception {
        String token = registerAndLogin("tx-mismatch@example.com");
        String accountId = createAccount(token, 100);
        String incomeCategoryId = createCategory(token, "Salario", "INCOME");

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\",\"categoryId\":\"" + incomeCategoryId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        String token = registerAndLogin("tx-amount@example.com");
        String accountId = createAccount(token, 100);

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":0,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transactionsAreIsolatedBetweenUsers() throws Exception {
        String ownerToken = registerAndLogin("tx-iso-a@example.com");
        String otherToken = registerAndLogin("tx-iso-b@example.com");
        String accountId = createAccount(ownerToken, 100);

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createsTransactionWithClientSuppliedIdAndRejectsDuplicate() throws Exception {
        String token = registerAndLogin("tx-client-id@example.com");
        String accountId = createAccount(token, 100);
        String clientId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + clientId + "\",\"type\":\"EXPENSE\",\"amount\":30,"
                                + "\"date\":\"2026-07-18\",\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(clientId));

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + clientId + "\",\"type\":\"EXPENSE\",\"amount\":10,"
                                + "\"date\":\"2026-07-18\",\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsTransactionWithoutToken() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void updatesOwnTransactionAndAdjustsBalance() throws Exception {
        String token = registerAndLogin("tx-update@example.com");
        String accountId = createAccount(token, 100);

        String createResponse = mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String txId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(put("/api/transactions/" + txId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50,\"date\":\"2026-07-18\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50));

        // El saldo refleja solo el monto nuevo (100 - 50 = 50), no el viejo + el nuevo.
        mockMvc.perform(get("/api/accounts/" + accountId + "/balance").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.balance").value(50));
    }

    @Test
    void deletesOwnTransactionAndRevertsBalance() throws Exception {
        String token = registerAndLogin("tx-delete@example.com");
        String accountId = createAccount(token, 100);

        String createResponse = mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String txId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(delete("/api/transactions/" + txId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/accounts/" + accountId + "/balance").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void rejectsUpdatingOrDeletingForeignTransaction() throws Exception {
        String ownerToken = registerAndLogin("tx-foreign-a@example.com");
        String otherToken = registerAndLogin("tx-foreign-b@example.com");
        String accountId = createAccount(ownerToken, 100);

        String createResponse = mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String txId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(put("/api/transactions/" + txId).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50,\"date\":\"2026-07-18\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/transactions/" + txId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    // FR-007: ver una transacción propia puntual; una ajena responde 404 (no 403) — mismo criterio
    // de aislamiento ya probado para editar/eliminar arriba.
    @Test
    void getsOwnTransactionAndRejectsForeignOne() throws Exception {
        String ownerToken = registerAndLogin("tx-get-a@example.com");
        String otherToken = registerAndLogin("tx-get-b@example.com");
        String accountId = createAccount(ownerToken, 100);

        String createResponse = mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":30,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String txId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(get("/api/transactions/" + txId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30));

        mockMvc.perform(get("/api/transactions/" + txId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersTransactionsByAccountCategoryAndDateRange() throws Exception {
        String token = registerAndLogin("tx-filter@example.com");
        String accountA = createAccount(token, 100);
        String accountB = createAccount(token, 100);
        String foodCategory = createCategory(token, "Comida", "EXPENSE");
        String transportCategory = createCategory(token, "Transporte", "EXPENSE");

        // accountA + foodCategory + fecha temprana
        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":10,\"date\":\"2026-07-01\","
                                + "\"accountId\":\"" + accountA + "\",\"categoryId\":\"" + foodCategory + "\"}"))
                .andExpect(status().isCreated());

        // accountB + transportCategory + fecha tardía
        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":20,\"date\":\"2026-07-25\","
                                + "\"accountId\":\"" + accountB + "\",\"categoryId\":\"" + transportCategory + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions?accountId=" + accountA).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(10));

        mockMvc.perform(get("/api/transactions?categoryId=" + transportCategory)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(20));

        mockMvc.perform(get("/api/transactions?dateFrom=2026-07-01&dateTo=2026-07-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(10));

        mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
