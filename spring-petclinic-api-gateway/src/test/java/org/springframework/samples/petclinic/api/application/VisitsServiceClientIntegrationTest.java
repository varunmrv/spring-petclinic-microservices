package org.springframework.samples.petclinic.api.application;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.api.dto.VisitDetails;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VisitsServiceClientIntegrationTest {

    private static final Integer PET_ID = 1;

    private VisitsServiceClient visitsServiceClient;

    private MockWebServer server;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        visitsServiceClient = new VisitsServiceClient(WebClient.builder());
        visitsServiceClient.setHostname(server.url("/").toString());
    }

    @AfterEach
    void shutdown() throws IOException {
        this.server.close();
    }

    @Test
    void getVisitsForPets_withAvailableVisitsService() {
        prepareResponse();

        Mono<Visits> visits = visitsServiceClient.getVisitsForPets(Collections.singletonList(1));

        assertVisitDescriptionEquals(visits.block(), PET_ID,"test visit");
    }

    /**
     * Test that updateVisit() sends a PUT request to the visits-service and correctly
     * deserialises the returned VisitDetails from the JSON response body.
     */
    @Test
    void updateVisit_withAvailableVisitsService() throws InterruptedException {
        MockResponse response = new MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("{\"id\":5,\"petId\":1,\"date\":\"2024-06-15\",\"description\":\"updated visit\"}")
            .build();
        server.enqueue(response);

        VisitDetails payload = new VisitDetails(5, 1, "2024-06-15", "updated visit");
        VisitDetails result = visitsServiceClient.updateVisit(1, 5, payload).block();

        assertNotNull(result);
        assertEquals(5, result.id());
        assertEquals(1, result.petId());
        assertEquals("2024-06-15", result.date());
        assertEquals("updated visit", result.description());

        // Verify the correct HTTP method and path were used
        RecordedRequest recordedRequest = server.takeRequest();
        assertEquals("PUT", recordedRequest.getMethod());
        assertEquals("/owners/*/pets/1/visits/5", recordedRequest.getPath());
    }

    /**
     * Test that deleteVisit() sends a DELETE request to the visits-service and completes
     * empty (Mono<Void>) when the server responds with 204 No Content.
     */
    @Test
    void deleteVisit_withAvailableVisitsService() throws InterruptedException {
        MockResponse response = new MockResponse.Builder()
            .code(204)
            .build();
        server.enqueue(response);

        Void result = visitsServiceClient.deleteVisit(1, 5).block();

        // A successful delete returns Mono<Void> which resolves to null
        assertNull(result);

        // Verify the correct HTTP method and path were used
        RecordedRequest recordedRequest = server.takeRequest();
        assertEquals("DELETE", recordedRequest.getMethod());
        assertEquals("/owners/*/pets/1/visits/5", recordedRequest.getPath());
    }


    private void assertVisitDescriptionEquals(Visits visits, int petId, String description) {
        assertEquals(1, visits.items().size());
        assertNotNull(visits.items().get(0));
        assertEquals(petId, visits.items().get(0).petId());
        assertEquals(description, visits.items().get(0).description());
    }

    private void prepareResponse() {
        MockResponse response = new MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("{\"items\":[{\"id\":5,\"date\":\"2018-11-15\",\"description\":\"test visit\",\"petId\":1}]}")
            .build();
        this.server.enqueue(response);
    }

}
