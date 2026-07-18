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
class CategoryControllerIT {

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
    void createsAndListsOwnCategories() throws Exception {
        String token = registerAndLogin("cat-owner@example.com");

        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Salario\",\"type\":\"INCOME\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Salario"));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Salario"));
    }

    @Test
    void rejectsDuplicateNameAndType() throws Exception {
        String token = registerAndLogin("cat-dup@example.com");

        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comida\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comida\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void categoriesAreIsolatedBetweenUsers() throws Exception {
        String ownerToken = registerAndLogin("cat-a@example.com");
        String otherToken = registerAndLogin("cat-b@example.com");

        String createResponse = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transporte\",\"type\":\"EXPENSE\"}"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/categories/" + categoryId).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hackeada\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/categories/" + categoryId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsSubcategoryAndRejectsCycle() throws Exception {
        String token = registerAndLogin("cat-hierarchy@example.com");

        String parentResponse = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comida\",\"type\":\"EXPENSE\"}"))
                .andReturn().getResponse().getContentAsString();
        String parentId = com.jayway.jsonpath.JsonPath.read(parentResponse, "$.id");

        String childResponse = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Supermercado\",\"type\":\"EXPENSE\",\"parentCategoryId\":\"" + parentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCategoryId").value(parentId))
                .andReturn().getResponse().getContentAsString();
        String childId = com.jayway.jsonpath.JsonPath.read(childResponse, "$.id");

        // Intentar que "Comida" pase a ser hija de su propia subcategoría "Supermercado" — ciclo.
        mockMvc.perform(put("/api/categories/" + parentId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comida\",\"type\":\"EXPENSE\",\"parentCategoryId\":\"" + childId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDeletingCategoryThatStillHasChildren() throws Exception {
        String token = registerAndLogin("cat-delete-parent@example.com");

        String parentResponse = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comida\",\"type\":\"EXPENSE\"}"))
                .andReturn().getResponse().getContentAsString();
        String parentId = com.jayway.jsonpath.JsonPath.read(parentResponse, "$.id");

        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Supermercado\",\"type\":\"EXPENSE\",\"parentCategoryId\":\"" + parentId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/" + parentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsCategoryWithoutToken() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    // FR-010 (feature 003): no se puede eliminar una categoría con transacciones asociadas — mismo
    // patrón (409 vía DataIntegrityViolationException) que ya se usa para subcategorías, ver
    // AccountExceptionHandler y research.md de la feature 003.
    @Test
    void rejectsDeletingCategoryThatHasTransactions() throws Exception {
        String token = registerAndLogin("cat-delete-with-tx@example.com");

        String accountResponse = mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Efectivo\",\"type\":\"CASH\",\"currency\":\"USD\",\"initialBalance\":100}"))
                .andReturn().getResponse().getContentAsString();
        String accountId = com.jayway.jsonpath.JsonPath.read(accountResponse, "$.id");

        String categoryResponse = mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comida\",\"type\":\"EXPENSE\"}"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = com.jayway.jsonpath.JsonPath.read(categoryResponse, "$.id");

        mockMvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":10,\"date\":\"2026-07-18\","
                                + "\"accountId\":\"" + accountId + "\",\"categoryId\":\"" + categoryId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/" + categoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }
}
