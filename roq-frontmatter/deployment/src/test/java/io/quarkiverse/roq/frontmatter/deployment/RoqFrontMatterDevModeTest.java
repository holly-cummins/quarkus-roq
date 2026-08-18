package io.quarkiverse.roq.frontmatter.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * Test HTTP HEAD method support in dev mode.
 * <p>
 * BUG REPRODUCED: HEAD requests return 404 while GET requests return 200 for the same URLs.
 * Verified with curl on actual blog in dev mode - consistently returns 404 for HEAD, 200 for GET.
 * <p>
 * In production mode, static files are generated and served by Quarkus's standard
 * static resource handler which correctly supports HEAD requests.
 * In dev mode, dynamic route handlers do not properly handle HEAD requests.
 */
@DisplayName("Roq FrontMatter - Dev Mode HTTP Methods")
public class RoqFrontMatterDevModeTest {

    private static final int DEV_MODE_PORT = 9382;

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site")
                    .addAsResource(
                            new org.jboss.shrinkwrap.api.asset.StringAsset(
                                    "quarkus.http.port=" + DEV_MODE_PORT + "\n" +
                                            "quarkus.roq.resource-dir=basic-site"),
                            "application.properties"));

    @Test
    @DisplayName("GET request to index page returns 200")
    public void testGetIndexPage() {
        RestAssured.given()
                .port(DEV_MODE_PORT)
                .get("/")
                .then()
                .statusCode(200)
                .log().ifValidationFails();
    }

    @Test
    @DisplayName("HEAD request to index page should return 200 (FAILS: returns 404)")
    public void testHeadIndexPage() {
        // BUG: This test expects 200 but currently fails with 404
        // even though GET on the same URL returns 200.
        // Verified with real blog:
        //   curl http://localhost:8095/docs/publishing -> 200 OK
        //   curl -I http://localhost:8095/docs/publishing -> 404 Not Found
        RestAssured.given()
                .port(DEV_MODE_PORT)
                .when()
                .head("/")
                .then()
                .log().all()
                .statusCode(200);
    }
}
