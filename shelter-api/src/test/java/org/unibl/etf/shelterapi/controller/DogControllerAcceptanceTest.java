package org.unibl.etf.shelterapi.controller;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.repository.DogRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DogControllerAcceptanceTest {

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

    @Autowired
    private DogRepository dogRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        dogRepository.deleteAll();
    }

    private Dog saveDog(String name, DogStatus status) {
        Dog dog = new Dog();
        dog.setName(name);
        dog.setStatus(status);
        return dogRepository.save(dog);
    }

    @Test
    void visitorBrowsesOnlyAvailableDogs() {
        saveDog("Rex", DogStatus.AVAILABLE);
        saveDog("Luna", DogStatus.SPONSORED);

        given()
                .when()
                .get("/api/dogs?status=AVAILABLE")
                .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("content[0].name", equalTo("Rex"))
                .body("page.totalElements", equalTo(1));
    }

    @Test
    void visitorOpensDogDetails() {
        Dog saved = saveDog("Rex", DogStatus.AVAILABLE);

        given()
                .when()
                .get("/api/dogs/{id}", saved.getId())
                .then()
                .statusCode(200)
                .body("name", equalTo("Rex"))
                .body("status", equalTo("AVAILABLE"));
    }

    @Test
    void visitorOpeningUnknownDogGetsNotFound() {
        given()
                .when()
                .get("/api/dogs/999999")
                .then()
                .statusCode(404);
    }

    @Test
    void anonymousVisitorCannotAddDog() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Rex\"}")
                .when()
                .post("/api/dogs")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403)));
    }
}
