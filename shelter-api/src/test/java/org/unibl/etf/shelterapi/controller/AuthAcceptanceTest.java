package org.unibl.etf.shelterapi.controller;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthAcceptanceTest {

    private static final String USER_CREDENTIAL = "strong-pass-1";
    private static final String AUTHORIZATION = "Authorization";
    private static final String DOG_JSON = "{\"name\":\"Rex\",\"breed\":\"Mixed\",\"birthYear\":2020}";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminSecret;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private static String registrationJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + USER_CREDENTIAL
                + "\",\"firstName\":\"Ana\",\"lastName\":\"Anic\"}";
    }

    private static String loginJson(String email, String secret) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + secret + "\"}";
    }

    private void register(String email) {
        given().contentType(ContentType.JSON).body(registrationJson(email))
                .when().post("/api/auth/register")
                .then().statusCode(201);
    }

    private String login(String email, String secret) {
        return given().contentType(ContentType.JSON).body(loginJson(email, secret))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .extract().path("accessToken");
    }

    @Test
    void visitorRegistersLogsInAndSeesOwnProfile() {
        String email = uniqueEmail();
        register(email);

        String token = login(email, USER_CREDENTIAL);

        given().header(AUTHORIZATION, "Bearer " + token)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("email", equalTo(email))
                .body("role", equalTo("USER"));
    }

    @Test
    void visitorCannotRegisterTheSameEmailTwice() {
        String email = uniqueEmail();
        register(email);

        given().contentType(ContentType.JSON).body(registrationJson(email))
                .when().post("/api/auth/register")
                .then().statusCode(409);
    }

    @Test
    void registrationWithInvalidDataIsRejected() {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"not-an-email\",\"password\":\"short\",\"firstName\":\"\",\"lastName\":\"\"}")
                .when().post("/api/auth/register")
                .then().statusCode(400);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        String email = uniqueEmail();
        register(email);

        given().contentType(ContentType.JSON).body(loginJson(email, "wrong-pass-1"))
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    void adminManagesDogsWhileVisitorsOnlyBrowse() {
        String token = login(adminEmail, adminSecret);

        int id = given().header(AUTHORIZATION, "Bearer " + token)
                .contentType(ContentType.JSON).body(DOG_JSON)
                .when().post("/api/dogs")
                .then().statusCode(201)
                .body("status", equalTo("AVAILABLE"))
                .extract().path("id");

        given().when().get("/api/dogs/{id}", id)
                .then().statusCode(200)
                .body("name", equalTo("Rex"));

        given().header(AUTHORIZATION, "Bearer " + token)
                .contentType(ContentType.JSON).body("{\"name\":\"Max\",\"status\":\"ADOPTED\"}")
                .when().put("/api/dogs/{id}", id)
                .then().statusCode(200)
                .body("name", equalTo("Max"))
                .body("status", equalTo("ADOPTED"));

        given().header(AUTHORIZATION, "Bearer " + token)
                .when().delete("/api/dogs/{id}", id)
                .then().statusCode(204);

        given().when().get("/api/dogs/{id}", id)
                .then().statusCode(404);
    }

    @Test
    void regularUserCannotCreateDog() {
        String email = uniqueEmail();
        register(email);
        String token = login(email, USER_CREDENTIAL);

        given().header(AUTHORIZATION, "Bearer " + token)
                .contentType(ContentType.JSON).body(DOG_JSON)
                .when().post("/api/dogs")
                .then().statusCode(403);
    }

    @Test
    void anonymousVisitorCannotCreateDog() {
        given().contentType(ContentType.JSON).body(DOG_JSON)
                .when().post("/api/dogs")
                .then().statusCode(401);
    }
}
