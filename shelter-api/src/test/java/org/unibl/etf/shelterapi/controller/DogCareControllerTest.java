package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.unibl.etf.shelterapi.dto.CareRequest;
import org.unibl.etf.shelterapi.dto.CareResponse;
import org.unibl.etf.shelterapi.dto.VaccinationResponse;
import org.unibl.etf.shelterapi.exception.AccessForbiddenException;
import org.unibl.etf.shelterapi.exception.ApiExceptionHandler;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.service.DogCareService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DogCareControllerTest {

    private static final String CARE = "/api/dogs/5/care";
    private static final String ANA = "ana@example.com";

    private final Authentication user = new TestingAuthenticationToken(ANA, null, "ROLE_USER");
    private final Authentication admin = new TestingAuthenticationToken("admin@example.com", null, "ROLE_ADMIN");

    @Mock
    private DogCareService careService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DogCareController(careService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static CareResponse response() {
        return new CareResponse(true, "123", "Healthy", null,
                List.of(new VaccinationResponse(1L, "Rabies", LocalDate.of(2026, 1, 1), null)), null);
    }

    @Test
    void getPassesTheUserAndMarksAUserAsNotAdmin() throws Exception {
        when(careService.getCare(5L, ANA, false)).thenReturn(response());

        mockMvc.perform(get(CARE).principal(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.microchipNumber").value("123"))
                .andExpect(jsonPath("$.vaccinations[0].name").value("Rabies"));
    }

    @Test
    void getMarksAnAdministratorAsAdmin() throws Exception {
        when(careService.getCare(5L, "admin@example.com", true)).thenReturn(response());

        mockMvc.perform(get(CARE).principal(admin))
                .andExpect(status().isOk());

        verify(careService).getCare(5L, "admin@example.com", true);
    }

    @Test
    void getReturnsForbiddenForSomeoneWhoIsNotTheSponsor() throws Exception {
        when(careService.getCare(5L, ANA, false)).thenThrow(new AccessForbiddenException("Only the sponsor"));

        mockMvc.perform(get(CARE).principal(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the sponsor"));
    }

    @Test
    void getReturnsNotFoundForAnUnknownDog() throws Exception {
        when(careService.getCare(5L, ANA, false)).thenThrow(new ResourceNotFoundException("Dog not found"));

        mockMvc.perform(get(CARE).principal(user))
                .andExpect(status().isNotFound());
    }

    @Test
    void putReturnsTheSavedDetails() throws Exception {
        when(careService.updateCare(eq(5L), any(CareRequest.class))).thenReturn(response());

        mockMvc.perform(put(CARE).principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sterilized\":true,\"microchipNumber\":\"123\","
                                + "\"vaccinations\":[{\"name\":\"Rabies\",\"vaccinatedOn\":\"2026-01-01\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sterilized").value(true));
    }

    @Test
    void putRejectsAVaccinationWithoutNameOrDate() throws Exception {
        mockMvc.perform(put(CARE).principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vaccinations\":[{\"name\":\" \",\"vaccinatedOn\":\"2026-01-01\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(CARE).principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vaccinations\":[{\"name\":\"Rabies\"}]}"))
                .andExpect(status().isBadRequest());

        verify(careService, never()).updateCare(any(), any());
    }

    @Test
    void putRejectsTooLongText() throws Exception {
        mockMvc.perform(put(CARE).principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"microchipNumber\":\"" + "1".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putRejectsMoreThanThirtyVaccinations() throws Exception {
        String one = "{\"name\":\"Rabies\",\"vaccinatedOn\":\"2026-01-01\"}";
        String body = "{\"vaccinations\":[" + String.join(",", java.util.Collections.nCopies(31, one)) + "]}";

        mockMvc.perform(put(CARE).principal(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putReturnsBadRequestWhenTheServiceRefusesTheDates() throws Exception {
        when(careService.updateCare(eq(5L), any(CareRequest.class)))
                .thenThrow(new InvalidRequestException("The next dose cannot be due before the vaccination date"));

        mockMvc.perform(put(CARE).principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The next dose cannot be due before the vaccination date"));
    }
}
