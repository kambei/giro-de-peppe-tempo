package com.sigeosrl.servicea.resouce;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class TestResourceTest {

    @Test
    void testHelloEndpoint() {

        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Service A"));
    }
}
