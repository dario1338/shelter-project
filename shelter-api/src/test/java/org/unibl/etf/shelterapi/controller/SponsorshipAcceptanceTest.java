package org.unibl.etf.shelterapi.controller;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SponsorshipAcceptanceTest {

    private static final String USER_CREDENTIAL = "strong-pass-1";
    private static final String AUTHORIZATION = "Authorization";
    private static final String APPLICATIONS = "/api/sponsorships";
    private static final String MY_APPLICATIONS = "/api/sponsorships/me";
    private static final String DOGS = "/api/dogs";

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

    private static RequestSpecification withToken(String token) {
        return given().header(AUTHORIZATION, "Bearer " + token);
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String login(String email, String secret) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + secret + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private String registerAndLogin() {
        String email = uniqueEmail();
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + USER_CREDENTIAL
                        + "\",\"firstName\":\"Ana\",\"lastName\":\"Anic\"}")
                .when().post("/api/auth/register")
                .then().statusCode(201);
        return login(email, USER_CREDENTIAL);
    }

    private String adminToken() {
        return login(adminEmail, adminSecret);
    }

    private int createDog(String adminToken) {
        return withToken(adminToken).contentType(ContentType.JSON)
                .body("{\"name\":\"Rex\",\"breed\":\"Mixed\",\"birthYear\":2020}")
                .when().post(DOGS)
                .then().statusCode(201)
                .extract().path("id");
    }

    private int applyFor(String userToken, int dogId) {
        return withToken(userToken).contentType(ContentType.JSON)
                .body("{\"dogId\":" + dogId + ",\"note\":\"I would like to help\"}")
                .when().post(APPLICATIONS)
                .then().statusCode(201)
                .body("status", equalTo("PENDING"))
                .extract().path("id");
    }

    @Test
    void adminApprovesApplicationAndDogBecomesSponsored() {
        String admin = adminToken();
        int dogId = createDog(admin);
        String user = registerAndLogin();
        int applicationId = applyFor(user, dogId);

        withToken(user).when().get(MY_APPLICATIONS)
                .then().statusCode(200)
                .body("content[0].status", equalTo("PENDING"));

        withToken(admin).when().get(APPLICATIONS + "?status=PENDING&size=100")
                .then().statusCode(200)
                .body("content.id", hasItem(applicationId));

        withToken(admin).contentType(ContentType.JSON).body("{\"adminNote\":\"Welcome\"}")
                .when().post(APPLICATIONS + "/{id}/approve", applicationId)
                .then().statusCode(200)
                .body("status", equalTo("APPROVED"))
                .body("adminNote", equalTo("Welcome"));

        given().when().get(DOGS + "/{id}", dogId)
                .then().statusCode(200)
                .body("status", equalTo("SPONSORED"));

        withToken(user).when().get(MY_APPLICATIONS)
                .then().statusCode(200)
                .body("content[0].status", equalTo("APPROVED"));
    }

    @Test
    void dogThatIsAlreadySponsoredCannotReceiveNewApplications() {
        String admin = adminToken();
        int dogId = createDog(admin);
        int applicationId = applyFor(registerAndLogin(), dogId);

        withToken(admin).contentType(ContentType.JSON).body("{}")
                .when().post(APPLICATIONS + "/{id}/approve", applicationId)
                .then().statusCode(200);

        withToken(registerAndLogin()).contentType(ContentType.JSON).body("{\"dogId\":" + dogId + "}")
                .when().post(APPLICATIONS)
                .then().statusCode(409);
    }

    @Test
    void userCannotSendTwoPendingApplicationsForTheSameDog() {
        int dogId = createDog(adminToken());
        String user = registerAndLogin();
        applyFor(user, dogId);

        withToken(user).contentType(ContentType.JSON).body("{\"dogId\":" + dogId + "}")
                .when().post(APPLICATIONS)
                .then().statusCode(409);
    }

    @Test
    void approvingOneApplicationRejectsTheOthersForTheSameDog() {
        String admin = adminToken();
        int dogId = createDog(admin);
        String winner = registerAndLogin();
        String loser = registerAndLogin();
        int winnerApplication = applyFor(winner, dogId);
        applyFor(loser, dogId);

        withToken(admin).contentType(ContentType.JSON).body("{}")
                .when().post(APPLICATIONS + "/{id}/approve", winnerApplication)
                .then().statusCode(200);

        withToken(loser).when().get(MY_APPLICATIONS)
                .then().statusCode(200)
                .body("content[0].status", equalTo("REJECTED"))
                .body("content[0].adminNote", notNullValue());
    }

    @Test
    void rejectedApplicationKeepsDogAvailableAndUserCanApplyAgain() {
        String admin = adminToken();
        int dogId = createDog(admin);
        String user = registerAndLogin();
        int applicationId = applyFor(user, dogId);

        withToken(admin).contentType(ContentType.JSON).body("{\"adminNote\":\"Not now\"}")
                .when().post(APPLICATIONS + "/{id}/reject", applicationId)
                .then().statusCode(200)
                .body("status", equalTo("REJECTED"));

        given().when().get(DOGS + "/{id}", dogId)
                .then().statusCode(200)
                .body("status", equalTo("AVAILABLE"));

        withToken(user).contentType(ContentType.JSON).body("{\"dogId\":" + dogId + "}")
                .when().post(APPLICATIONS)
                .then().statusCode(201);
    }

    @Test
    void userCancelsOnlyTheirOwnPendingApplication() {
        int dogId = createDog(adminToken());
        String owner = registerAndLogin();
        String stranger = registerAndLogin();
        int applicationId = applyFor(owner, dogId);

        withToken(stranger).when().post(APPLICATIONS + "/{id}/cancel", applicationId)
                .then().statusCode(404);

        withToken(owner).when().post(APPLICATIONS + "/{id}/cancel", applicationId)
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"));

        withToken(owner).when().post(APPLICATIONS + "/{id}/cancel", applicationId)
                .then().statusCode(409);
    }

    @Test
    void myApplicationsCanBeFilteredByDogAndStatus() {
        String admin = adminToken();
        int firstDog = createDog(admin);
        int secondDog = createDog(admin);
        String user = registerAndLogin();
        int cancelled = applyFor(user, firstDog);
        withToken(user).when().post(APPLICATIONS + "/{id}/cancel", cancelled).then().statusCode(200);
        int pending = applyFor(user, firstDog);
        applyFor(user, secondDog);

        withToken(user).when().get(MY_APPLICATIONS + "?dogId=" + firstDog)
                .then().statusCode(200)
                .body("page.totalElements", equalTo(2));

        withToken(user).when().get(MY_APPLICATIONS + "?dogId=" + firstDog + "&status=PENDING")
                .then().statusCode(200)
                .body("page.totalElements", equalTo(1))
                .body("content[0].id", equalTo(pending));

        withToken(user).when().get(MY_APPLICATIONS + "?status=PENDING")
                .then().statusCode(200)
                .body("page.totalElements", equalTo(2));

        withToken(registerAndLogin()).when().get(MY_APPLICATIONS + "?dogId=" + firstDog)
                .then().statusCode(200)
                .body("page.totalElements", equalTo(0));
    }

    @Test
    void accessIsRestrictedByRole() {
        String admin = adminToken();
        int dogId = createDog(admin);
        String user = registerAndLogin();
        int applicationId = applyFor(user, dogId);

        given().contentType(ContentType.JSON).body("{\"dogId\":" + dogId + "}")
                .when().post(APPLICATIONS)
                .then().statusCode(401);

        withToken(user).when().get(APPLICATIONS)
                .then().statusCode(403);

        withToken(user).when().post(APPLICATIONS + "/{id}/approve", applicationId)
                .then().statusCode(403);

        withToken(admin).contentType(ContentType.JSON).body("{\"dogId\":" + dogId + "}")
                .when().post(APPLICATIONS)
                .then().statusCode(403);
    }

    @Test
    void applyingValidatesTheRequest() {
        String user = registerAndLogin();

        withToken(user).contentType(ContentType.JSON).body("{\"dogId\":999999}")
                .when().post(APPLICATIONS)
                .then().statusCode(404);

        withToken(user).contentType(ContentType.JSON).body("{\"note\":\"no dog\"}")
                .when().post(APPLICATIONS)
                .then().statusCode(400);
    }
}
