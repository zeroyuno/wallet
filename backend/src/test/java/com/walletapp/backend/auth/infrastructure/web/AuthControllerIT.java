package com.walletapp.backend.auth.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

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

    @Test
    void registersNewUserSuccessfully() throws Exception {
        String requestBody = """
                {"email":"ana@example.com","password":"password123","displayName":"Ana"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        String requestBody = """
                {"email":"dup@example.com","password":"password123","displayName":"Dup"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidEmailFormat() throws Exception {
        String requestBody = """
                {"email":"not-an-email","password":"password123","displayName":"X"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsAccessTokenForValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"login-ok@example.com","password":"password123","displayName":"Login Ok"}
                """)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"login-ok@example.com","password":"password123"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void rejectsLoginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"login-wrong@example.com","password":"password123","displayName":"Login Wrong"}
                """)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"login-wrong@example.com","password":"not-the-password"}
                """)).andExpect(status().isUnauthorized());
    }

    @Test
    void locksAccountAfterTooManyFailedAttempts() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"login-lockout@example.com","password":"password123","displayName":"Login Lockout"}
                """)).andExpect(status().isCreated());

        String wrongLogin = """
                {"email":"login-lockout@example.com","password":"not-the-password"}
                """;

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongLogin))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongLogin))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsAuthenticatedUserAndLogoutRevokesToken() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"me-flow@example.com","password":"password123","displayName":"Me Flow"}
                """)).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"me-flow@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.accessToken");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me-flow@example.com"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
