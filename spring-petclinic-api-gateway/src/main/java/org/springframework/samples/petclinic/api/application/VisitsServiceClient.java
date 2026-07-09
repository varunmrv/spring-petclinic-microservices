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
package org.springframework.samples.petclinic.api.application;

import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.api.dto.VisitDetails;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * @author Maciej Szarlinski
 */
@Component
public class VisitsServiceClient {

    // Could be changed for testing purpose
    private String hostname = "http://visits-service/";

    private final WebClient.Builder webClientBuilder;

    public VisitsServiceClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<Visits> getVisitsForPets(final List<Integer> petIds) {
        return webClientBuilder.build()
            .get()
            .uri(hostname + "pets/visits?petId={petId}", joinIds(petIds))
            .retrieve()
            .bodyToMono(Visits.class);
    }

    /**
     * Updates an existing visit in the visits-service.
     *
     * @param petId     the ID of the pet the visit belongs to
     * @param visitId   the ID of the visit to update
     * @param visit     the updated visit details (date and/or description)
     * @return a {@link Mono} emitting the updated {@link VisitDetails}, or an error
     *         Mono with a 404 status if the visit does not exist
     */
    public Mono<VisitDetails> updateVisit(final int petId, final int visitId, final VisitDetails visit) {
        return webClientBuilder.build()
            .put()
            .uri(hostname + "owners/*/pets/{petId}/visits/{visitId}", petId, visitId)
            .bodyValue(visit)
            .retrieve()
            .onStatus(HttpStatus.NOT_FOUND::equals,
                response -> Mono.error(new WebClientResponseException(
                    HttpStatus.NOT_FOUND.value(),
                    HttpStatus.NOT_FOUND.getReasonPhrase(),
                    response.headers().asHttpHeaders(),
                    null, null)))
            .bodyToMono(VisitDetails.class);
    }

    /**
     * Deletes an existing visit in the visits-service.
     *
     * @param petId   the ID of the pet the visit belongs to
     * @param visitId the ID of the visit to delete
     * @return a {@link Mono} completing empty on success (204 No Content), or an error
     *         Mono with a 404 status if the visit does not exist
     */
    public Mono<Void> deleteVisit(final int petId, final int visitId) {
        return webClientBuilder.build()
            .delete()
            .uri(hostname + "owners/*/pets/{petId}/visits/{visitId}", petId, visitId)
            .retrieve()
            .onStatus(HttpStatus.NOT_FOUND::equals,
                response -> Mono.error(new WebClientResponseException(
                    HttpStatus.NOT_FOUND.value(),
                    HttpStatus.NOT_FOUND.getReasonPhrase(),
                    response.headers().asHttpHeaders(),
                    null, null)))
            .bodyToMono(Void.class);
    }

    private String joinIds(List<Integer> petIds) {
        return petIds.stream().map(Object::toString).collect(joining(","));
    }

    void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
