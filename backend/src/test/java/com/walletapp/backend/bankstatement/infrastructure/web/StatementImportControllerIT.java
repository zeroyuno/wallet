package com.walletapp.backend.bankstatement.infrastructure.web;

import com.walletapp.backend.bankstatement.application.FakePdfExtractionGateway;
import com.walletapp.backend.bankstatement.application.PdfExtractionGateway;
import com.walletapp.backend.bankstatement.application.dto.ExtractedTransactionDto;
import com.walletapp.backend.bankstatement.application.dto.UnparsedLineDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StatementImportControllerIT {

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
        PdfExtractionGateway pdfExtractionGateway() {
            return new FakePdfExtractionGateway()
                    .withTransactions(
                            new ExtractedTransactionDto(LocalDate.of(2026, 1, 15), new BigDecimal("50"), "EXPENSE",
                                    "Supermercado"),
                            new ExtractedTransactionDto(LocalDate.of(2026, 1, 20), new BigDecimal("1200"), "INCOME",
                                    "Sueldo"))
                    .withUnparsedLines(new UnparsedLineDto("$??? linea rara", "monto ilegible"));
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

    private String createAccount(String token) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cuenta Bcp\",\"type\":\"BANK\",\"currency\":\"USD\","
                                + "\"initialBalance\":100}"))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private String uploadStatement(String token, String accountId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "estado.pdf", "application/pdf",
                "contenido de prueba".getBytes());
        String response = mockMvc.perform(multipart("/api/statement-imports")
                        .file(file)
                        .param("accountId", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private String waitForCompletion(String token, String importId) throws Exception {
        String status = "IN_PROGRESS";
        for (int i = 0; i < 50 && "IN_PROGRESS".equals(status); i++) {
            Thread.sleep(100);
            String response = mockMvc.perform(get("/api/statement-imports/" + importId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            status = com.jayway.jsonpath.JsonPath.read(response, "$.status");
        }
        return status;
    }

    @Test
    void importsTransactionsFromAPdfStatement() throws Exception {
        String token = registerAndLogin("statement-full@example.com");
        String accountId = createAccount(token);

        String importId = uploadStatement(token, accountId);
        String finalStatus = waitForCompletion(token, importId);

        assertThat(finalStatus).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/statement-imports/" + importId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsImported").value(2))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].reason").value("monto ilegible"));

        mockMvc.perform(get("/api/transactions?accountId=" + accountId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoryId").doesNotExist());
    }

    @Test
    void rejectsAccountNotOwnedByUser() throws Exception {
        String ownerToken = registerAndLogin("statement-owner@example.com");
        String otherToken = registerAndLogin("statement-other@example.com");
        String accountId = createAccount(ownerToken);

        MockMultipartFile file = new MockMultipartFile("file", "estado.pdf", "application/pdf", "x".getBytes());
        mockMvc.perform(multipart("/api/statement-imports")
                        .file(file)
                        .param("accountId", accountId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    // FR-006, SC-003: subir el mismo PDF dos veces no duplica movimientos.
    @Test
    void reUploadingTheSameStatementDoesNotDuplicateTransactions() throws Exception {
        String token = registerAndLogin("statement-rerun@example.com");
        String accountId = createAccount(token);

        String firstImportId = uploadStatement(token, accountId);
        waitForCompletion(token, firstImportId);

        String secondImportId = uploadStatement(token, accountId);
        waitForCompletion(token, secondImportId);

        mockMvc.perform(get("/api/transactions?accountId=" + accountId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void importsAreIsolatedBetweenUsers() throws Exception {
        String ownerToken = registerAndLogin("statement-iso-a@example.com");
        String otherToken = registerAndLogin("statement-iso-b@example.com");
        String accountId = createAccount(ownerToken);

        String importId = uploadStatement(ownerToken, accountId);

        mockMvc.perform(get("/api/statement-imports/" + importId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/statement-imports/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
