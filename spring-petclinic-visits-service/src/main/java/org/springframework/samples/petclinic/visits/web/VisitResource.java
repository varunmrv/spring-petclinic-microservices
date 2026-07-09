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
package org.springframework.samples.petclinic.visits.web;

import java.util.Date;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.visits.model.Visit;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Maciej Szarlinski
 * @author Ramazan Sakin
 */
@RestController
@Timed("petclinic.visit")
class VisitResource {

    private static final Logger log = LoggerFactory.getLogger(VisitResource.class);

    private final VisitRepository visitRepository;

    VisitResource(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @PostMapping("owners/*/pets/{petId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public Visit create(
        @Valid @RequestBody Visit visit,
        @PathVariable("petId") @Min(1) int petId) {

        visit.setPetId(petId);
        log.info("Saving visit {}", visit);
        return visitRepository.save(visit);
    }

    @GetMapping("owners/*/pets/{petId}/visits")
    public List<Visit> read(@PathVariable("petId") @Min(1) int petId) {
        return visitRepository.findByPetId(petId);
    }

    @GetMapping("pets/visits")
    public Visits read(@RequestParam("petId") List<Integer> petIds) {
        final List<Visit> byPetIdIn = visitRepository.findByPetIdIn(petIds);
        return new Visits(byPetIdIn);
    }

    /**
     * Updates the {@code date} and/or {@code description} of an existing visit.
     *
     * @param visitRequest the update payload (date, description)
     * @param petId        the pet that owns the visit
     * @param visitId      the ID of the visit to update
     * @return the updated {@link Visit} entity
     * @throws VisitNotFoundException if no visit with {@code visitId} exists
     */
    @PutMapping("owners/*/pets/{petId}/visits/{visitId}")
    public Visit update(
        @Valid @RequestBody VisitRequest visitRequest,
        @PathVariable("petId") @Min(1) int petId,
        @PathVariable("visitId") @Min(1) int visitId) {

        Visit visit = visitRepository.findById(visitId)
            .orElseThrow(() -> new VisitNotFoundException(visitId));

        if (visitRequest.date() != null) {
            visit.setDate(visitRequest.date());
        }
        if (visitRequest.description() != null) {
            visit.setDescription(visitRequest.description());
        }

        log.info("Updating visit {}", visit);
        return visitRepository.save(visit);
    }

    /**
     * Deletes an existing visit by ID.
     *
     * @param petId   the pet that owns the visit (used for URL consistency)
     * @param visitId the ID of the visit to delete
     * @throws VisitNotFoundException if no visit with {@code visitId} exists
     */
    @DeleteMapping("owners/*/pets/{petId}/visits/{visitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable("petId") @Min(1) int petId,
        @PathVariable("visitId") @Min(1) int visitId) {

        if (!visitRepository.existsById(visitId)) {
            throw new VisitNotFoundException(visitId);
        }
        log.info("Deleting visit {}", visitId);
        visitRepository.deleteById(visitId);
    }

    record Visits(
        List<Visit> items
    ) {
    }

    /**
     * DTO carrying the mutable fields of a {@link Visit} for update operations.
     * Decouples the API contract from the JPA entity, preventing clients from
     * overwriting {@code id} or {@code petId}.
     *
     * <p>Mirrors the {@code OwnerRequest}/{@code PetRequest} pattern in
     * {@code customers-service}.
     */
    record VisitRequest(
        Date date,
        String description
    ) {
    }
}
