package org.unibl.etf.shelterapi.smoke;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Post-deploy smoke tests. They run against an already deployed environment
 * (no Spring context, no Docker) and are skipped unless -Dstaging.url is set.
 */
@Tag("smoke")
@EnabledIfSystemProperty(named = "staging.url", matches = ".+")
class StagingSmokeTest {

    @BeforeAll
    static void configureBaseUri() {
        RestAssured.baseURI = System.getProperty("staging.url");
    }

    @AfterAll
    static void resetRestAssured() {
        RestAssured.reset();
    }

    @Test
    void healthEndpointReportsOk() {
        given()
                .when().get("/api/health")
                .then().statusCode(200).body("status", equalTo("OK"));
    }

    @Test
    void infoEndpointReportsExpectedRevision() {
        ValidatableResponse response = given()
                .when().get("/api/info")
                .then().statusCode(200).body("revision", notNullValue());

        String expected = System.getProperty("staging.expectedRevision");
        if (expected != null && !expected.isBlank()) {
            response.body("revision", equalTo(expected));
        }
    }

    @Test
    void protectedEndpointDeniesAnonymousAccess() {
        // The path is deliberately non-existent: security rules deny before routing.
        given()
                .when().get("/api/protected-probe")
                .then().statusCode(anyOf(is(401), is(403)));
    }
}