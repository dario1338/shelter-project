package org.unibl.etf.shelterapi.controller;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.unibl.etf.shelterapi.service.TestImages;
import org.unibl.etf.shelterapi.support.InMemoryImageStorage;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Photos are public to read, but only an administrator can add or remove them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DogImageAcceptanceTest {

    private static final String USER_CREDENTIAL = "strong-pass-1";
    private static final String AUTHORIZATION = "Authorization";
    private static final String DOGS = "/api/dogs";
    private static final String IMAGES = "/api/dogs/{id}/images";
    private static final String IMAGE = "/api/dogs/{id}/images/{imageId}";
    private static final String FILE = "file";

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

    @Autowired
    private InMemoryImageStorage storage;

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

    private String userToken() {
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

    private int upload(String adminToken, int dogId) {
        return withToken(adminToken)
                .multiPart(FILE, "dog.jpg", TestImages.jpeg(64, 48), "image/jpeg")
                .when().post(IMAGES, dogId)
                .then().statusCode(201)
                .extract().path("id");
    }

    @Test
    void adminUploadsPhotosAndEveryoneSeesThem() {
        String admin = adminToken();
        int dogId = createDog(admin);

        String firstUrl = withToken(admin)
                .multiPart(FILE, "dog.jpg", TestImages.jpeg(64, 48), "image/jpeg")
                .when().post(IMAGES, dogId)
                .then().statusCode(201)
                .body("url", org.hamcrest.Matchers.startsWith("https://images.shelter.test/dogs/" + dogId + "/"))
                .extract().path("url");
        upload(admin, dogId);

        assertTrue(storage.contains(InMemoryImageStorage.keyOf(firstUrl)));
        given().when().get(DOGS + "/{id}", dogId)
                .then().statusCode(200)
                .body("primaryImageUrl", equalTo(firstUrl))
                .body("images", hasSize(2))
                .body("images[0].url", equalTo(firstUrl));
        given().when().get(DOGS + "?size=100")
                .then().statusCode(200)
                .body("content.find { it.id == " + dogId + " }.primaryImageUrl", equalTo(firstUrl));
    }

    @Test
    void dogWithoutPhotosHasNoPrimaryImage() {
        int dogId = createDog(adminToken());

        given().when().get(DOGS + "/{id}", dogId)
                .then().statusCode(200)
                .body("primaryImageUrl", nullValue())
                .body("images", hasSize(0));
    }

    @Test
    void onlyAdministratorsCanAddOrRemovePhotos() {
        String admin = adminToken();
        int dogId = createDog(admin);
        int imageId = upload(admin, dogId);
        String user = userToken();

        given().multiPart(FILE, "dog.jpg", TestImages.jpeg(8, 8), "image/jpeg")
                .when().post(IMAGES, dogId).then().statusCode(401);
        withToken(user).multiPart(FILE, "dog.jpg", TestImages.jpeg(8, 8), "image/jpeg")
                .when().post(IMAGES, dogId).then().statusCode(403);
        given().when().delete(IMAGE, dogId, imageId).then().statusCode(401);
        withToken(user).when().delete(IMAGE, dogId, imageId).then().statusCode(403);

        given().when().get(DOGS + "/{id}", dogId).then().body("images", hasSize(1));
    }

    @Test
    void fileThatIsNotAnImageIsRejectedAndNothingIsStored() {
        String admin = adminToken();
        int dogId = createDog(admin);
        int filesBefore = storage.keys().size();

        withToken(admin)
                .multiPart(FILE, "dog.jpg", "this is not an image".getBytes(), "image/jpeg")
                .when().post(IMAGES, dogId)
                .then().statusCode(400);

        assertEquals(filesBefore, storage.keys().size());
    }

    @Test
    void uploadForAnUnknownDogIsNotFound() {
        withToken(adminToken())
                .multiPart(FILE, "dog.jpg", TestImages.jpeg(8, 8), "image/jpeg")
                .when().post(IMAGES, 999_999)
                .then().statusCode(404);
    }

    @Test
    void tooBigFileIsRejected() {
        String admin = adminToken();
        int dogId = createDog(admin);

        withToken(admin)
                .multiPart(FILE, "dog.jpg", new byte[6 * 1024 * 1024], "image/jpeg")
                .when().post(IMAGES, dogId)
                .then().statusCode(413);
    }

    @Test
    void dogCanHaveAtMostSixPhotos() {
        String admin = adminToken();
        int dogId = createDog(admin);
        for (int i = 0; i < 6; i++) {
            upload(admin, dogId);
        }

        withToken(admin)
                .multiPart(FILE, "dog.jpg", TestImages.jpeg(8, 8), "image/jpeg")
                .when().post(IMAGES, dogId)
                .then().statusCode(409);
    }

    @Test
    void deletingAPhotoRemovesItAndItsFile() {
        String admin = adminToken();
        int dogId = createDog(admin);
        int first = upload(admin, dogId);
        int second = upload(admin, dogId);
        String firstKey = InMemoryImageStorage.keyOf(
                given().when().get(DOGS + "/{id}", dogId).path("images[0].url"));

        withToken(admin).when().delete(IMAGE, dogId, first).then().statusCode(204);

        assertFalse(storage.contains(firstKey));
        given().when().get(DOGS + "/{id}", dogId)
                .then().body("images", hasSize(1))
                .body("images[0].id", equalTo(second));
    }

    @Test
    void photoCannotBeDeletedThroughAnotherDog() {
        String admin = adminToken();
        int dogId = createDog(admin);
        int otherDog = createDog(admin);
        int imageId = upload(admin, dogId);

        withToken(admin).when().delete(IMAGE, otherDog, imageId).then().statusCode(404);

        given().when().get(DOGS + "/{id}", dogId).then().body("images", hasSize(1));
    }

    @Test
    void deletingADogRemovesTheFilesOfItsPhotos() {
        String admin = adminToken();
        int dogId = createDog(admin);
        upload(admin, dogId);
        String key = InMemoryImageStorage.keyOf(
                given().when().get(DOGS + "/{id}", dogId).path("images[0].url"));
        assertTrue(storage.contains(key));

        withToken(admin).when().delete(DOGS + "/{id}", dogId).then().statusCode(204);

        assertFalse(storage.contains(key));
    }
}
