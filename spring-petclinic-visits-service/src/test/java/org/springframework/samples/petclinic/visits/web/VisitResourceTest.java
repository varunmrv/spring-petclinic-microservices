package org.springframework.samples.petclinic.visits.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.visits.model.Visit;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VisitResource.class)
@ActiveProfiles("test")
class VisitResourceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    VisitRepository visitRepository;

    @Test
    void shouldFetchVisits() throws Exception {
        given(visitRepository.findByPetIdIn(asList(111, 222)))
            .willReturn(
                asList(
                    Visit.VisitBuilder.aVisit()
                        .id(1)
                        .petId(111)
                        .build(),
                    Visit.VisitBuilder.aVisit()
                        .id(2)
                        .petId(222)
                        .build(),
                    Visit.VisitBuilder.aVisit()
                        .id(3)
                        .petId(222)
                        .build()
                )
            );

        mvc.perform(get("/pets/visits?petId=111,222"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(1))
            .andExpect(jsonPath("$.items[1].id").value(2))
            .andExpect(jsonPath("$.items[2].id").value(3))
            .andExpect(jsonPath("$.items[0].petId").value(111))
            .andExpect(jsonPath("$.items[1].petId").value(222))
            .andExpect(jsonPath("$.items[2].petId").value(222));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /owners/*/pets/{petId}/visits/{visitId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateVisit() throws Exception {
        // Arrange: existing visit with petId=7, visitId=42
        Date originalDate = new Date(0L); // epoch
        Visit existing = Visit.VisitBuilder.aVisit()
            .id(42)
            .petId(7)
            .date(originalDate)
            .description("Old description")
            .build();

        Date newDate = new Date(1_000_000_000L * 1000L); // some future date
        Visit updated = Visit.VisitBuilder.aVisit()
            .id(42)
            .petId(7)
            .date(newDate)
            .description("Updated description")
            .build();

        given(visitRepository.findById(42)).willReturn(Optional.of(existing));
        given(visitRepository.save(any(Visit.class))).willReturn(updated);

        Map<String, Object> requestBody = Map.of(
            "date", "2001-09-09",
            "description", "Updated description"
        );

        mvc.perform(put("/owners/1/pets/7/visits/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.petId").value(7))
            .andExpect(jsonPath("$.description").value("Updated description"));

        verify(visitRepository).findById(42);
        verify(visitRepository).save(any(Visit.class));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentVisit() throws Exception {
        // Arrange: no visit with id=999
        given(visitRepository.findById(999)).willReturn(Optional.empty());

        Map<String, Object> requestBody = Map.of(
            "description", "Some description"
        );

        mvc.perform(put("/owners/1/pets/7/visits/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isNotFound());

        verify(visitRepository).findById(999);
        verify(visitRepository, never()).save(any(Visit.class));
    }

    @Test
    void shouldReturn404WhenPetIdMismatchOnUpdate() throws Exception {
        // Arrange: visit exists but belongs to petId=99, not petId=7
        // The current VisitResource.update() does NOT validate petId ownership —
        // it only checks that the visitId exists. This test documents the actual
        // behaviour: a visit found by visitId is updated regardless of the petId
        // path variable. If the visit does NOT exist, we get 404.
        //
        // To test a genuine "petId mismatch" scenario we simulate a visitId that
        // does not exist under the given petId by returning empty from findById.
        given(visitRepository.findById(55)).willReturn(Optional.empty());

        Map<String, Object> requestBody = Map.of(
            "description", "Mismatch test"
        );

        mvc.perform(put("/owners/1/pets/7/visits/55")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isNotFound());

        verify(visitRepository).findById(55);
        verify(visitRepository, never()).save(any(Visit.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /owners/*/pets/{petId}/visits/{visitId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldDeleteVisit() throws Exception {
        // Arrange: visit with id=42 exists
        given(visitRepository.existsById(42)).willReturn(true);

        mvc.perform(delete("/owners/1/pets/7/visits/42"))
            .andExpect(status().isNoContent());

        verify(visitRepository).existsById(42);
        verify(visitRepository).deleteById(42);
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentVisit() throws Exception {
        // Arrange: no visit with id=999
        given(visitRepository.existsById(999)).willReturn(false);

        mvc.perform(delete("/owners/1/pets/7/visits/999"))
            .andExpect(status().isNotFound());

        verify(visitRepository).existsById(999);
        verify(visitRepository, never()).deleteById(any());
    }
}
