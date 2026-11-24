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
import org.unibl.etf.shelterapi.service.VisitService;

import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VisitAcceptanceTest {

    private static final String USER_CREDENTIAL = "strong-pass-1";
    private static final String AUTHORIZATION = "Authorization";
    private static final String VISITS = "/api/visits";
    private static final String MY_VISITS = "/api/visits/me";

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

    private int createDog() {
        return withToken(login(adminEmail, adminSecret)).contentType(ContentType.JSON)
                .body("{\"name\":\"Rex\",\"breed\":\"Mixed\",\"birthYear\":2020}")
                .when().post("/api/dogs")
                .then().statusCode(201)
                .extract().path("id");
    }

    // A day that is always in the future, a week from today in the shelter.
    private static LocalDate nextWeek() {
        return LocalDate.now(VisitService.SHELTER_ZONE).plusDays(7);
    }

    private static String visit(int dogId, LocalDate date, int hour) {
        return "{\"dogId\":" + dogId + ",\"date\":\"" + date + "\",\"hour\":" + hour + "}";
    }

    @Test
    void userBooksAVisitAndSeesItAmongTheirVisits() {
        int dogId = createDog();
        String user = registerAndLogin();
        LocalDate date = nextWeek();
        String visitAt = date.atTime(11, 0).atZone(VisitService.SHELTER_ZONE).toInstant().toString();

        withToken(user).contentType(ContentType.JSON).body(visit(dogId, date, 11))
                .when().post(VISITS)
                .then().statusCode(201)
                .body("status", equalTo("BOOKED"))
                .body("dogName", equalTo("Rex"))
                .body("visitAt", equalTo(visitAt));

        withToken(user).when().get(MY_VISITS)
                .then().statusCode(200)
                .body("page.totalElements", equalTo(1))
                .body("content[0].visitAt", equalTo(visitAt));
    }

    @Test
    void aTimeThatIsBookedCannotBeBookedByAnotherUser() {
        int dogId = createDog();
        LocalDate date = nextWeek();

        withToken(registerAndLogin()).contentType(ContentType.JSON).body(visit(dogId, date, 12))
                .when().post(VISITS)
                .then().statusCode(201);

        withToken(registerAndLogin()).contentType(ContentType.JSON).body(visit(dogId, date, 12))
                .when().post(VISITS)
                .then().statusCode(409);
    }

    @Test
    void aVisitCannotBeBookedInThePastOrOutsideOpeningHours() {
        int dogId = createDog();
        String user = registerAndLogin();

        withToken(user).contentType(ContentType.JSON)
                .body(visit(dogId, LocalDate.now(VisitService.SHELTER_ZONE).minusDays(1), 10))
                .when().post(VISITS)
                .then().statusCode(400);

        withToken(user).contentType(ContentType.JSON).body(visit(dogId, nextWeek(), 16))
                .when().post(VISITS)
                .then().statusCode(400);
    }

    @Test
    void anAnonymousVisitorCannotBookOrListVisits() {
        int dogId = createDog();

        given().contentType(ContentType.JSON).body(visit(dogId, nextWeek(), 10))
                .when().post(VISITS)
                .then().statusCode(401);

        given().when().get(MY_VISITS)
                .then().statusCode(401);
    }
}
