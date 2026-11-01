package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AuthControllerIntegrationTest {

    private static final String USER_CREDENTIAL = "strong-pass-1";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.admin.email}")
    private String adminEmail;

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private static String registrationJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + USER_CREDENTIAL
                + "\",\"firstName\":\"Ana\",\"lastName\":\"Anic\"}";
    }

    @Test
    void registerCreatesUserWithoutExposingPassword() {
        String email = uniqueEmail();

        restTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registrationJson(email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains(email), "Expected email in body: " + body);
                    assertFalse(body.contains(USER_CREDENTIAL), "Password must not be returned: " + body);
                    assertFalse(body.toLowerCase().contains("hash"), "Hash must not be returned: " + body);
                });
    }

    @Test
    void registeringTheSameEmailTwiceReturnsConflict() {
        String email = uniqueEmail();

        restTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registrationJson(email))
                .exchange()
                .expectStatus().isCreated();

        restTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registrationJson(email))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        String email = uniqueEmail();

        restTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registrationJson(email))
                .exchange()
                .expectStatus().isCreated();

        restTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"wrong-pass-1\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void profileRequiresToken() {
        restTestClient.get()
                .uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidTokenIsRejected() {
        restTestClient.get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer not-a-valid-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void initialAdminIsCreatedAtStartup() {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();

        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.getPasswordHash().startsWith("$2"), "Password must be stored as a BCrypt hash");
    }
}
