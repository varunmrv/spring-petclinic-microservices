package org.springframework.samples.petclinic.api.boundary.web;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.api.application.CustomersServiceClient;
import org.springframework.samples.petclinic.api.application.VisitsServiceClient;
import org.springframework.samples.petclinic.api.dto.OwnerDetails;
import org.springframework.samples.petclinic.api.dto.PetDetails;
import org.springframework.samples.petclinic.api.dto.VisitDetails;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@WebFluxTest(controllers = ApiGatewayController.class)
@Import({ReactiveResilience4JAutoConfiguration.class, CircuitBreakerConfiguration.class})
class ApiGatewayControllerTest {

    @MockitoBean
    private CustomersServiceClient customersServiceClient;

    @MockitoBean
    private VisitsServiceClient visitsServiceClient;

    @Autowired
    private WebTestClient client;


    @Test
    void getOwnerDetails_withAvailableVisitsService() {
        PetDetails cat = PetDetails.PetDetailsBuilder.aPetDetails()
            .id(20)
            .name("Garfield")
            .visits(new ArrayList<>())
            .build();
        OwnerDetails owner = OwnerDetails.OwnerDetailsBuilder.anOwnerDetails()
            .pets(List.of(cat))
            .build();
        Mockito
            .when(customersServiceClient.getOwner(1))
            .thenReturn(Mono.just(owner));

        VisitDetails visit = new VisitDetails(300, cat.id(), null, "First visit");
        Visits visits = new Visits(List.of(visit));
        Mockito
            .when(visitsServiceClient.getVisitsForPets(Collections.singletonList(cat.id())))
            .thenReturn(Mono.just(visits));

        client.get()
            .uri("/api/gateway/owners/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.pets[0].name").isEqualTo("Garfield")
            .jsonPath("$.pets[0].visits[0].description").isEqualTo("First visit");
    }

    /**
     * Test Resilience4j fallback method
     */
    @Test
    void getOwnerDetails_withServiceError() {
        PetDetails cat = PetDetails.PetDetailsBuilder.aPetDetails()
            .id(20)
            .name("Garfield")
            .visits(new ArrayList<>())
            .build();
        OwnerDetails owner = OwnerDetails.OwnerDetailsBuilder.anOwnerDetails()
            .pets(List.of(cat))
            .build();
        Mockito
            .when(customersServiceClient.getOwner(1))
            .thenReturn(Mono.just(owner));

        Mockito
            .when(visitsServiceClient.getVisitsForPets(Collections.singletonList(cat.id())))
            .thenReturn(Mono.error(new ConnectException("Simulate error")));

        client.get()
            .uri("/api/gateway/owners/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.pets[0].name").isEqualTo("Garfield")
            .jsonPath("$.pets[0].visits").isEmpty();
    }

    /**
     * Test that PUT /api/gateway/owners/*/pets/{petId}/visits/{visitId} proxies the
     * update to VisitsServiceClient and returns the updated VisitDetails on success.
     */
    @Test
    void updateVisit_withAvailableVisitsService() {
        VisitDetails updatedVisit = new VisitDetails(300, 20, "2024-06-15", "Annual checkup");

        Mockito
            .when(visitsServiceClient.updateVisit(20, 300, updatedVisit))
            .thenReturn(Mono.just(updatedVisit));

        client.put()
            .uri("/api/gateway/owners/*/pets/20/visits/300")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updatedVisit)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(300)
            .jsonPath("$.petId").isEqualTo(20)
            .jsonPath("$.date").isEqualTo("2024-06-15")
            .jsonPath("$.description").isEqualTo("Annual checkup");
    }

    /**
     * Test that PUT /api/gateway/owners/*/pets/{petId}/visits/{visitId} returns 404
     * when the visits-service signals that the visit does not exist.
     */
    @Test
    void updateVisit_withServiceError() {
        VisitDetails visitPayload = new VisitDetails(999, 20, "2024-06-15", "Non-existent visit");

        Mockito
            .when(visitsServiceClient.updateVisit(20, 999, visitPayload))
            .thenReturn(Mono.error(new WebClientResponseException(
                NOT_FOUND.value(),
                NOT_FOUND.getReasonPhrase(),
                null, null, null)));

        client.put()
            .uri("/api/gateway/owners/*/pets/20/visits/999")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(visitPayload)
            .exchange()
            .expectStatus().isNotFound();
    }

    /**
     * Test that DELETE /api/gateway/owners/*/pets/{petId}/visits/{visitId} proxies the
     * delete to VisitsServiceClient and returns 204 No Content on success.
     */
    @Test
    void deleteVisit_withAvailableVisitsService() {
        Mockito
            .when(visitsServiceClient.deleteVisit(20, 300))
            .thenReturn(Mono.empty());

        client.delete()
            .uri("/api/gateway/owners/*/pets/20/visits/300")
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();
    }

    /**
     * Test that DELETE /api/gateway/owners/*/pets/{petId}/visits/{visitId} returns 404
     * when the visits-service signals that the visit does not exist.
     */
    @Test
    void deleteVisit_withServiceError() {
        Mockito
            .when(visitsServiceClient.deleteVisit(20, 999))
            .thenReturn(Mono.error(new WebClientResponseException(
                NOT_FOUND.value(),
                NOT_FOUND.getReasonPhrase(),
                null, null, null)));

        client.delete()
            .uri("/api/gateway/owners/*/pets/20/visits/999")
            .exchange()
            .expectStatus().isNotFound();
    }

}
