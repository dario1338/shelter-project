package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.unibl.etf.shelterapi.dto.VisitRequest;
import org.unibl.etf.shelterapi.dto.VisitResponse;
import org.unibl.etf.shelterapi.exception.ApiExceptionHandler;
import org.unibl.etf.shelterapi.exception.ConflictException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.VisitStatus;
import org.unibl.etf.shelterapi.service.VisitService;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VisitControllerTest {

    private static final String ANA = "ana@example.com";
    private static final String VISITS = "/api/visits";

    private final Principal ana = new TestingAuthenticationToken(ANA, null);

    @Mock
    private VisitService visitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VisitController(visitService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private static VisitResponse booked() {
        return new VisitResponse(20L, 5L, "Rex", Instant.parse("2026-12-01T09:00:00Z"), VisitStatus.BOOKED,
                Instant.parse("2026-11-24T12:00:00Z"));
    }

    @Test
    void bookReturnsTheCreatedVisit() throws Exception {
        when(visitService.book(eq(ANA), any(VisitRequest.class))).thenReturn(booked());

        mockMvc.perform(post(VISITS).principal(ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogId\":5,\"date\":\"2026-12-01\",\"hour\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.dogName").value("Rex"));
    }

    // An hour before opening, an hour whose visit would end after closing, and a request without a date.
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"dogId\":5,\"date\":\"2026-12-01\",\"hour\":9}",
            "{\"dogId\":5,\"date\":\"2026-12-01\",\"hour\":16}",
            "{\"dogId\":5,\"hour\":10}"
    })
    void bookRejectsAnInvalidRequest(String body) throws Exception {
        mockMvc.perform(post(VISITS).principal(ana).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(visitService);
    }

    @Test
    void bookReturnsConflictWhenTheTimeIsTaken() throws Exception {
        when(visitService.book(eq(ANA), any(VisitRequest.class)))
                .thenThrow(new ConflictException("This time is already booked for this dog"));

        mockMvc.perform(post(VISITS).principal(ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogId\":5,\"date\":\"2026-12-01\",\"hour\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This time is already booked for this dog"));
    }

    @Test
    void cancelReturnsTheCancelledVisit() throws Exception {
        when(visitService.cancel(ANA, 20L)).thenReturn(new VisitResponse(20L, 5L, "Rex",
                Instant.parse("2026-12-01T09:00:00Z"), VisitStatus.CANCELLED, Instant.parse("2026-11-24T12:00:00Z")));

        mockMvc.perform(post(VISITS + "/20/cancel").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelReturnsNotFoundForAVisitOfAnotherUser() throws Exception {
        when(visitService.cancel(ANA, 20L)).thenThrow(new ResourceNotFoundException("Visit with id 20 not found"));

        mockMvc.perform(post(VISITS + "/20/cancel").principal(ana))
                .andExpect(status().isNotFound());
    }

    @Test
    void myVisitsReturnsPagedContent() throws Exception {
        when(visitService.getMyVisits(eq(ANA), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(booked())));

        mockMvc.perform(get(VISITS + "/me").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].dogName").value("Rex"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
}
