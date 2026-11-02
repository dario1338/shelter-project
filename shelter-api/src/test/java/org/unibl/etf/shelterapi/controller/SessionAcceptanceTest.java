package org.unibl.etf.shelterapi.controller;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SessionAcceptanceTest {

    private static final String PASSWORD = "strong-pass-1";
    private static final String NEW_PASSWORD = "brand-new-pass-2";
    private static final String WRONG_PASSWORD = "wrong-pass-0";
    private static final String ORIGIN = "https://app.shelter.test";
    private static final String COOKIE = "refresh_token";
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String LOGIN = "/api/auth/login";
    private static final String REFRESH = "/api/auth/refresh";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String ME = "/api/auth/me";
    private static final String CHANGE_PASSWORD = "/api/auth/change-password";
    private static final int ALLOWED_FAILURES = 5;

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

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private String registerUser() {
        String email = UUID.randomUUID() + "@example.com";
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                        + "\",\"firstName\":\"Ana\",\"lastName\":\"Anic\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);
        return email;
    }

    private Response login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when().post(LOGIN);
    }

    private Response refresh(String refreshToken) {
        return given().header("Origin", ORIGIN).cookie(COOKIE, refreshToken).when().post(REFRESH);
    }

    private String loginAndGetRefreshToken(String email) {
        Response response = login(email, PASSWORD);
        response.then().statusCode(200);
        return response.getCookie(COOKIE);
    }

    @Test
    void loginIssuesAShortLivedAccessTokenAndAHardenedRefreshCookie() {
        String email = registerUser();

        login(email, PASSWORD).then()
                .statusCode(200)
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(900))
                .body("refreshToken", nullValue())
                .header(SET_COOKIE, allOf(
                        containsString(COOKIE + "="),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth")));
    }

    @Test
    void refreshRotatesTheTokenAndTheNewAccessTokenWorks() {
        String email = registerUser();
        String first = loginAndGetRefreshToken(email);

        Response refreshed = refresh(first);
        refreshed.then().statusCode(200);
        String second = refreshed.getCookie(COOKIE);
        String accessToken = refreshed.path("accessToken");

        assertNotNull(second);
        assertNotEquals(first, second);
        given().header("Authorization", "Bearer " + accessToken)
                .when().get(ME)
                .then().statusCode(200).body("email", equalTo(email));
    }

    @Test
    void replayingAUsedRefreshTokenRevokesTheWholeFamily() {
        String first = loginAndGetRefreshToken(registerUser());
        Response refreshed = refresh(first);
        refreshed.then().statusCode(200);
        String second = refreshed.getCookie(COOKIE);

        refresh(first).then().statusCode(401);
        refresh(second).then().statusCode(401);
    }

    @Test
    void refreshRequiresAnAllowedOrigin() {
        String token = loginAndGetRefreshToken(registerUser());

        given().cookie(COOKIE, token).when().post(REFRESH).then().statusCode(403);
        given().header("Origin", "https://evil.example").cookie(COOKIE, token)
                .when().post(REFRESH).then().statusCode(403);

        // The rejected requests did not touch the token.
        refresh(token).then().statusCode(200);
    }

    @Test
    void refreshWithoutACookieIsUnauthorized() {
        given().header("Origin", ORIGIN).when().post(REFRESH).then().statusCode(401);
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() {
        String token = loginAndGetRefreshToken(registerUser());

        given().header("Origin", ORIGIN).cookie(COOKIE, token)
                .when().post(LOGOUT)
                .then().statusCode(204).header(SET_COOKIE, containsString("Max-Age=0"));

        refresh(token).then().statusCode(401);
    }

    @Test
    void logoutWithoutACookieStillSucceeds() {
        given().header("Origin", ORIGIN).when().post(LOGOUT).then().statusCode(204);
    }

    @Test
    void anAccountIsLockedAfterRepeatedFailures() {
        String email = registerUser();
        for (int i = 0; i < ALLOWED_FAILURES; i++) {
            login(email, WRONG_PASSWORD).then().statusCode(401);
        }

        login(email, PASSWORD).then().statusCode(429).header("Retry-After", notNullValue());
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCounter() {
        String email = registerUser();
        for (int i = 0; i < ALLOWED_FAILURES - 1; i++) {
            login(email, WRONG_PASSWORD).then().statusCode(401);
        }
        login(email, PASSWORD).then().statusCode(200);
        for (int i = 0; i < ALLOWED_FAILURES - 1; i++) {
            login(email, WRONG_PASSWORD).then().statusCode(401);
        }

        login(email, PASSWORD).then().statusCode(200);
    }

    @Test
    void changingThePasswordSignsTheUserOutEverywhere() {
        String email = registerUser();
        Response session = login(email, PASSWORD);
        session.then().statusCode(200);
        String accessToken = session.path("accessToken");
        String refreshToken = session.getCookie(COOKIE);

        given().header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}")
                .when().post(CHANGE_PASSWORD)
                .then().statusCode(204);

        refresh(refreshToken).then().statusCode(401);
        login(email, PASSWORD).then().statusCode(401);
        login(email, NEW_PASSWORD).then().statusCode(200);
    }

    @Test
    void changingThePasswordRequiresTheCurrentPassword() {
        String email = registerUser();
        String accessToken = login(email, PASSWORD).path("accessToken");

        given().header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{\"currentPassword\":\"" + WRONG_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}")
                .when().post(CHANGE_PASSWORD)
                .then().statusCode(400);
    }
}