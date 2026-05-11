package com.telepaxx.assignment;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for GET /search.
 *
 * Scope: HTTP contract, wiring, and real DICOM file loading.
 *
 * @QuarkusTest starts a real Quarkus container. RosterLoader loads all 13
 *              DICOM files from test-data/roster via @PostConstruct. No
 *              mocking.
 *
 *              Test data reference:
 *              P1001 → Müller / MÜLLER — 3 files: 1174.dcm, 4831.dcm, 9027.dcm
 *              P1008 → Böhm — 1 file: 9980.dcm
 */
@QuarkusTest
class PatientSearchResourceTest {

    @Test
    void searchByPatientId_returns200WithCorrectRecord() {
        // app boots, RosterLoader reads real DICOM files, response is JSON
        given()
                .queryParam("patientId", "P1008")
                .when()
                .get("/search")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(1))
                .body("[0].patientId", equalTo("P1008"))
                .body("[0].lastName", equalTo("Böhm"))
                .body("[0].fileName", equalTo("9980.dcm"));
    }

    @Test
    void searchByPatientId_multipleFiles_returnsAllFiles() {
        // same patientId across multiple DICOM files all come back
        given()
                .queryParam("patientId", "P1001")
                .when()
                .get("/search")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("patientId", everyItem(equalTo("P1001")))
                .body("fileName", hasItems("1174.dcm", "4831.dcm", "9027.dcm"));
    }

    @Test
    void searchByLastName_caseInsensitive_returnsMatchingRecords() {
        // lastName search works through the full HTTP stack
        given()
                .queryParam("lastName", "böhm")
                .when()
                .get("/search")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].patientId", equalTo("P1008"));
    }

    @Test
    void searchWithNoMatch_returns200WithEmptyArray() {
        // no results - 200 + [] (not 404)
        given()
                .queryParam("patientId", "P9999X")
                .when()
                .get("/search")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void searchWithNoParams_returns400() {
        // missing both params - 400 (validation lives in the resource layer)
        given()
                .when()
                .get("/search")
                .then()
                .statusCode(400);
    }

    @Test
    void searchWithBlankParams_returns400() {
        given()
                .queryParam("patientId", "   ")
                .when()
                .get("/search")
                .then()
                .statusCode(400);
    }

    @Test
    void responseContainsAllExpectedFields() {
        // JSON serialisation of PatientRecord includes all four fields
        given()
                .queryParam("patientId", "P1008")
                .when()
                .get("/search")
                .then()
                .statusCode(200)
                .body("[0]", hasKey("patientId"))
                .body("[0]", hasKey("lastName"))
                .body("[0]", hasKey("firstName"))
                .body("[0]", hasKey("fileName"));
    }
}