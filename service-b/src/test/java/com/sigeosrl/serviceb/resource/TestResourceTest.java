package com.sigeosrl.serviceb.resource;

import com.sigeosrl.serviceb.config.WireMockExtensions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class TestResourceTest {

    @Test
    void testHelloEndpoint() {

        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Service B, that called Service A too: Hello from Service A"));
    }
}
