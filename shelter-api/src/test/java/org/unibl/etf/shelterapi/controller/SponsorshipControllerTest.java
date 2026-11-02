package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.unibl.etf.shelterapi.dto.SponsorshipRequest;
import org.unibl.etf.shelterapi.dto.SponsorshipResponse;
import org.unibl.etf.shelterapi.exception.ApiExceptionHandler;
import org.unibl.etf.shelterapi.exception.ConflictException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.service.SponsorshipService;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SponsorshipControllerTest {

    private static final String ANA = "ana@example.com";
    private static final String APPLICATIONS = "/api/sponsorships";

    private final Principal ana = new TestingAuthenticationToken(ANA, null);

    @Mock
    private SponsorshipService sponsorshipService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SponsorshipController(sponsorshipService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private static SponsorshipResponse response(ApplicationStatus status) {
        return new SponsorshipResponse(10L, 5L, "Rex", ANA, "Ana Anic", status, "note", null, null, null);
    }

    @Test
    void applyReturnsCreatedApplication() throws Exception {
        when(sponsorshipService.apply(eq(ANA), any(SponsorshipRequest.class)))
                .thenReturn(response(ApplicationStatus.PENDING));

        mockMvc.perform(post(APPLICATIONS)
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogId\":5,\"note\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.dogName").value("Rex"));
    }

    @Test
    void applyRejectsMissingDogId() throws Exception {
        mockMvc.perform(post(APPLICATIONS)
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyReturnsConflictWhenDogIsNotAvailable() throws Exception {
        when(sponsorshipService.apply(eq(ANA), any(SponsorshipRequest.class)))
                .thenThrow(new ConflictException("Dog is not available for sponsorship"));

        mockMvc.perform(post(APPLICATIONS)
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogId\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Dog is not available for sponsorship"));
    }

    @Test
    void myApplicationsReturnsPagedContent() throws Exception {
        when(sponsorshipService.getMyApplications(eq(ANA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(ApplicationStatus.PENDING))));

        mockMvc.perform(get(APPLICATIONS + "/me").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void applicationsCanBeFilteredByStatus() throws Exception {
        when(sponsorshipService.getApplications(eq(ApplicationStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(ApplicationStatus.APPROVED))));

        mockMvc.perform(get(APPLICATIONS).param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"));
    }

    @Test
    void applicationsRejectUnknownStatus() throws Exception {
        mockMvc.perform(get(APPLICATIONS).param("status", "FLYING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveReturnsApprovedApplicationWithNote() throws Exception {
        when(sponsorshipService.approve(10L, "Welcome")).thenReturn(response(ApplicationStatus.APPROVED));

        mockMvc.perform(post(APPLICATIONS + "/10/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"Welcome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveWorksWithoutBody() throws Exception {
        when(sponsorshipService.approve(10L, null)).thenReturn(response(ApplicationStatus.APPROVED));

        mockMvc.perform(post(APPLICATIONS + "/10/approve"))
                .andExpect(status().isOk());

        verify(sponsorshipService).approve(10L, null);
    }

    @Test
    void rejectReturnsRejectedApplication() throws Exception {
        when(sponsorshipService.reject(10L, "Not now")).thenReturn(response(ApplicationStatus.REJECTED));

        mockMvc.perform(post(APPLICATIONS + "/10/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"Not now\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void approveReturnsNotFoundForUnknownApplication() throws Exception {
        when(sponsorshipService.approve(99L, null))
                .thenThrow(new ResourceNotFoundException("Application with id 99 not found"));

        mockMvc.perform(post(APPLICATIONS + "/99/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseConstraintViolationIsReportedAsConflict() throws Exception {
        when(sponsorshipService.approve(10L, null))
                .thenThrow(new DataIntegrityViolationException("duplicate approved application"));

        mockMvc.perform(post(APPLICATIONS + "/10/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"));
    }

    @Test
    void cancelReturnsCancelledApplication() throws Exception {
        when(sponsorshipService.cancel(ANA, 10L)).thenReturn(response(ApplicationStatus.CANCELLED));

        mockMvc.perform(post(APPLICATIONS + "/10/cancel").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
