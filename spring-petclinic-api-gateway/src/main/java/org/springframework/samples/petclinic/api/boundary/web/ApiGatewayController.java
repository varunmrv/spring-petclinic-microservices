/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.api.boundary.web;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.api.application.CustomersServiceClient;
import org.springframework.samples.petclinic.api.application.VisitsServiceClient;
import org.springframework.samples.petclinic.api.dto.OwnerDetails;
import org.springframework.samples.petclinic.api.dto.VisitDetails;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * @author Maciej Szarlinski
 */
@RestController
@RequestMapping("/api/gateway")
public class ApiGatewayController {

    private final CustomersServiceClient customersServiceClient;

    private final VisitsServiceClient visitsServiceClient;

    private final ReactiveCircuitBreakerFactory cbFactory;

    public ApiGatewayController(CustomersServiceClient customersServiceClient,
                                VisitsServiceClient visitsServiceClient,
                                ReactiveCircuitBreakerFactory cbFactory) {
        this.customersServiceClient = customersServiceClient;
        this.visitsServiceClient = visitsServiceClient;
        this.cbFactory = cbFactory;
    }

    @GetMapping(value = "owners/{ownerId}")
    public Mono<OwnerDetails> getOwnerDetails(final @PathVariable int ownerId) {
        return customersServiceClient.getOwner(ownerId)
            .flatMap(owner ->
                visitsServiceClient.getVisitsForPets(owner.getPetIds())
                    .transform(it -> {
                        ReactiveCircuitBreaker cb = cbFactory.create("getOwnerDetails");
                        return cb.run(it, throwable -> emptyVisitsForPets());
                    })
                    .map(addVisitsToOwner(owner))
            );

    }

    /**
     * Updates an existing visit for a pet.
     *
     * @param petId   the ID of the pet
     * @param visitId the ID of the visit to update
     * @param visit   the updated visit payload (date and/or description)
     * @return a {@link Mono} emitting the updated {@link VisitDetails} on success (200 OK),
     *         or a 404 Not Found response if the visit does not exist
     */
    @PutMapping("owners/*/pets/{petId}/visits/{visitId}")
    public Mono<VisitDetails> updateVisit(
            @PathVariable("petId") int petId,
            @PathVariable("visitId") int visitId,
            @RequestBody VisitDetails visit) {
        return visitsServiceClient.updateVisit(petId, visitId, visit)
            .onErrorMap(
                WebClientResponseException.class,
                ex -> ex.getStatusCode() == HttpStatus.NOT_FOUND
                    ? new VisitNotFoundException(visitId)
                    : ex);
    }

    /**
     * Deletes an existing visit for a pet.
     *
     * @param petId   the ID of the pet
     * @param visitId the ID of the visit to delete
     * @return a {@link Mono} completing empty on success (204 No Content),
     *         or a 404 Not Found response if the visit does not exist
     */
    @DeleteMapping("owners/*/pets/{petId}/visits/{visitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteVisit(
            @PathVariable("petId") int petId,
            @PathVariable("visitId") int visitId) {
        return visitsServiceClient.deleteVisit(petId, visitId)
            .onErrorMap(
                WebClientResponseException.class,
                ex -> ex.getStatusCode() == HttpStatus.NOT_FOUND
                    ? new VisitNotFoundException(visitId)
                    : ex);
    }

    private Function<Visits, OwnerDetails> addVisitsToOwner(OwnerDetails owner) {
        return visits -> {
            owner.pets()
                .forEach(pet -> pet.visits()
                    .addAll(visits.items().stream()
                        .filter(v -> v.petId() == pet.id())
                        .toList())
                );
            return owner;
        };
    }

    private Mono<Visits> emptyVisitsForPets() {
        return Mono.just(new Visits(List.of()));
    }
}
