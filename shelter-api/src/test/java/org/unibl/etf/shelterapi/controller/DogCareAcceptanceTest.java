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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/**
 * Care details are visible to administrators and to the approved sponsor of the dog only.
 * The cases below cover every kind of caller, because the rule is the point of the feature.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DogCareAcceptanceTest {

    private static final String USER_CREDENTIAL = "strong-pass-1";
    private static final String AUTHORIZATION = "Authorization";
    private static final String APPLICATIONS = "/api/sponsorships";
    private static final String DOGS = "/api/dogs";
    private static final String CARE = "/api/dogs/{id}/care";
    private static final String CARE_BODY = "{\"sterilized\":true,\"microchipNumber\":\"900123\","
            + "\"healthNotes\":\"Healthy\",\"internalNote\":\"Bites strangers\","
            + "\"vaccinations\":[{\"name\":\"Rabies\",\"vaccinatedOn\":\"2026-01-01\",\"nextDueOn\":\"2027-01-01\"},"
            + "{\"name\":\"Distemper\",\"vaccinatedOn\":\"2026-03-01\"}]}";

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

    private String login(String email, String secret) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + secret + "\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private String registerAndLogin() {
        String email = UUID.randomUUID() + "@example.com";
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
                .body("{\"dogId\":" + dogId + "}")
                .when().post(APPLICATIONS)
                .then().statusCode(201)
                .extract().path("id");
    }

    private void decide(String admin, int applicationId, String decision) {
        withToken(admin).contentType(ContentType.JSON).body("{}")
                .when().post(APPLICATIONS + "/{id}/" + decision, applicationId)
                .then().statusCode(200);
    }

    private void saveCare(String admin, int dogId) {
        withToken(admin).contentType(ContentType.JSON).body(CARE_BODY)
                .when().put(CARE, dogId)
                .then().statusCode(200);
    }

    @Test
    void anonymousVisitorCannotReadOrChange() {
        int dogId = createDog(adminToken());

        given().when().get(CARE, dogId).then().statusCode(401);
        given().contentType(ContentType.JSON).body(CARE_BODY).when().put(CARE, dogId).then().statusCode(401);
    }

    @Test
    void adminSavesAndReadsEverything() {
        String admin = adminToken();
        int dogId = createDog(admin);

        withToken(admin).contentType(ContentType.JSON).body(CARE_BODY)
                .when().put(CARE, dogId)
                .then().statusCode(200)
                .body("internalNote", equalTo("Bites strangers"))
                .body("vaccinations", hasSize(2));

        withToken(admin).when().get(CARE, dogId)
                .then().statusCode(200)
                .body("sterilized", equalTo(true))
                .body("microchipNumber", equalTo("900123"))
                .body("internalNote", equalTo("Bites strangers"))
                // newest vaccination first
                .body("vaccinations[0].name", equalTo("Distemper"))
                .body("vaccinations[1].name", equalTo("Rabies"))
                .body("vaccinations[1].nextDueOn", equalTo("2027-01-01"));
    }

    @Test
    void dogWithoutDetailsHasAnEmptyAnswerForTheAdmin() {
        String admin = adminToken();
        int dogId = createDog(admin);

        withToken(admin).when().get(CARE, dogId)
                .then().statusCode(200)
                .body("vaccinations", empty())
                .body("sterilized", nullValue());
    }

    @Test
    void userWhoIsNotASponsorGetsForbidden() {
        String admin = adminToken();
        int dogId = createDog(admin);
        saveCare(admin, dogId);

        withToken(registerAndLogin()).when().get(CARE, dogId).then().statusCode(403);
    }

    @Test
    void pendingRejectedAndCancelledApplicationsGiveNoAccess() {
        String admin = adminToken();
        int dogId = createDog(admin);
        saveCare(admin, dogId);
        String pending = registerAndLogin();
        String rejected = registerAndLogin();
        String cancelled = registerAndLogin();
        applyFor(pending, dogId);
        decide(admin, applyFor(rejected, dogId), "reject");
        int cancelledApplication = applyFor(cancelled, dogId);
        withToken(cancelled).when().post(APPLICATIONS + "/{id}/cancel", cancelledApplication)
                .then().statusCode(200);

        withToken(pending).when().get(CARE, dogId).then().statusCode(403);
        withToken(rejected).when().get(CARE, dogId).then().statusCode(403);
        withToken(cancelled).when().get(CARE, dogId).then().statusCode(403);
    }

    @Test
    void sponsorOfAnotherDogGetsForbidden() {
        String admin = adminToken();
        int sponsoredDog = createDog(admin);
        int otherDog = createDog(admin);
        saveCare(admin, otherDog);
        String sponsor = registerAndLogin();
        decide(admin, applyFor(sponsor, sponsoredDog), "approve");

        withToken(sponsor).when().get(CARE, otherDog).then().statusCode(403);
    }

    @Test
    void approvedSponsorSeesTheDetailsButNotTheInternalNote() {
        String admin = adminToken();
        int dogId = createDog(admin);
        saveCare(admin, dogId);
        String sponsor = registerAndLogin();
        decide(admin, applyFor(sponsor, dogId), "approve");

        withToken(sponsor).when().get(CARE, dogId)
                .then().statusCode(200)
                .body("microchipNumber", equalTo("900123"))
                .body("vaccinations", hasSize(2))
                .body("internalNote", nullValue());
    }

    @Test
    void sponsorKeepsAccessWhenTheDogIsAdopted() {
        String admin = adminToken();
        int dogId = createDog(admin);
        saveCare(admin, dogId);
        String sponsor = registerAndLogin();
        decide(admin, applyFor(sponsor, dogId), "approve");

        withToken(admin).contentType(ContentType.JSON).body("{\"name\":\"Rex\",\"status\":\"ADOPTED\"}")
                .when().put(DOGS + "/{id}", dogId)
                .then().statusCode(200);

        withToken(sponsor).when().get(CARE, dogId).then().statusCode(200);
    }

    @Test
    void onlyAdminCanChangeTheDetails() {
        String admin = adminToken();
        int dogId = createDog(admin);
        String sponsor = registerAndLogin();
        decide(admin, applyFor(sponsor, dogId), "approve");

        withToken(sponsor).contentType(ContentType.JSON).body(CARE_BODY)
                .when().put(CARE, dogId)
                .then().statusCode(403);
        withToken(registerAndLogin()).contentType(ContentType.JSON).body(CARE_BODY)
                .when().put(CARE, dogId)
                .then().statusCode(403);
    }

    @Test
    void updateReplacesTheVaccinations() {
        String admin = adminToken();
        int dogId = createDog(admin);
        saveCare(admin, dogId);

        withToken(admin).contentType(ContentType.JSON).body("{\"vaccinations\":[]}")
                .when().put(CARE, dogId)
                .then().statusCode(200)
                .body("vaccinations", empty())
                .body("microchipNumber", nullValue());

        withToken(admin).when().get(CARE, dogId)
                .then().statusCode(200)
                .body("vaccinations", empty());
    }

    @Test
    void updateValidatesTheRequest() {
        String admin = adminToken();
        int dogId = createDog(admin);

        withToken(admin).contentType(ContentType.JSON)
                .body("{\"vaccinations\":[{\"name\":\"Rabies\",\"vaccinatedOn\":\"2026-02-01\",\"nextDueOn\":\"2026-01-01\"}]}")
                .when().put(CARE, dogId)
                .then().statusCode(400);
        withToken(admin).contentType(ContentType.JSON).body("{\"vaccinations\":[{\"name\":\"Rabies\"}]}")
                .when().put(CARE, dogId)
                .then().statusCode(400);
    }

    @Test
    void unknownDogIsNotFoundAndDeletingTheDogRemovesTheDetails() {
        String admin = adminToken();

        withToken(admin).when().get(CARE, 999999).then().statusCode(404);
        withToken(admin).contentType(ContentType.JSON).body(CARE_BODY).when().put(CARE, 999999).then().statusCode(404);

        int dogId = createDog(admin);
        saveCare(admin, dogId);
        withToken(admin).when().delete(DOGS + "/{id}", dogId).then().statusCode(204);
        withToken(admin).when().get(CARE, dogId).then().statusCode(404);
    }

    @Test
    void careDetailsAreNotPartOfThePublicDogResponse() {
        String admin = adminToken();
        int dogId = createDog(admin);
        saveCare(admin, dogId);

        given().when().get(DOGS + "/{id}", dogId)
                .then().statusCode(200)
                .body("microchipNumber", nullValue())
                .body("internalNote", nullValue())
                .body("vaccinations", nullValue());
    }
}
