package com.walletapp.backend.account.infrastructure.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIT {

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

    @Test
    void createsAndListsOwnAccounts() throws Exception {
        String token = registerAndLogin("acc-owner@example.com");

        mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Efectivo\",\"type\":\"CASH\",\"currency\":\"USD\",\"initialBalance\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Efectivo"));

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Efectivo"));
    }

    @Test
    void accountsAreIsolatedBetweenUsers() throws Exception {
        String ownerToken = registerAndLogin("acc-a@example.com");
        String otherToken = registerAndLogin("acc-b@example.com");

        String createResponse = mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Banco\",\"type\":\"BANK\",\"currency\":\"USD\",\"initialBalance\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        // El dueño ve su propia lista con la cuenta.
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$[0].id").value(accountId));

        // El otro usuario no la ve en su lista.
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // Ni puede editarla ni eliminarla — 404, no 403.
        mockMvc.perform(put("/api/accounts/" + accountId).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hackeada\",\"type\":\"BANK\",\"currency\":\"USD\",\"initialBalance\":0}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/accounts/" + accountId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesAndDeletesOwnAccount() throws Exception {
        String token = registerAndLogin("acc-update@example.com");

        String createResponse = mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Efectivo\",\"type\":\"CASH\",\"currency\":\"USD\",\"initialBalance\":50}"))
                .andReturn().getResponse().getContentAsString();
        String accountId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(put("/api/accounts/" + accountId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Efectivo (billetera)\",\"type\":\"CASH\",\"currency\":\"USD\",\"initialBalance\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Efectivo (billetera)"));

        mockMvc.perform(delete("/api/accounts/" + accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsAccountWithoutToken() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }
}
