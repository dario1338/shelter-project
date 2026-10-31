package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.repository.DogRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class DogControllerIntegrationTest {

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
    private DogRepository dogRepository;

    @BeforeEach
    void cleanDatabase() {
        dogRepository.deleteAll();
    }

    private Dog saveDog(String name, DogStatus status) {
        Dog dog = new Dog();
        dog.setName(name);
        dog.setBreed("Mixed");
        dog.setBirthYear(2021);
        dog.setStatus(status);
        return dogRepository.save(dog);
    }

    @Test
    void listReturnsSavedDogsFromRealDatabase() {
        saveDog("Rex", DogStatus.AVAILABLE);

        restTestClient.get()
                .uri("/api/dogs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("Rex"), "Expected Rex in body: " + body));
    }

    @Test
    void listFiltersByStatus() {
        saveDog("Rex", DogStatus.AVAILABLE);
        saveDog("Luna", DogStatus.ADOPTED);

        restTestClient.get()
                .uri("/api/dogs?status=ADOPTED")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("Luna"), "Expected Luna in body: " + body);
                    assertTrue(!body.contains("Rex"), "Did not expect Rex in body: " + body);
                });
    }

    @Test
    void getByIdReturnsDog() {
        Dog saved = saveDog("Rex", DogStatus.AVAILABLE);

        restTestClient.get()
                .uri("/api/dogs/" + saved.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("Rex"), "Expected Rex in body: " + body));
    }

    @Test
    void getByIdReturnsNotFoundForUnknownDog() {
        restTestClient.get()
                .uri("/api/dogs/999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createWithoutAuthenticationIsRejected() {
        restTestClient.post()
                .uri("/api/dogs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Rex\"}")
                .exchange()
                .expectStatus().is4xxClientError();
    }
}
