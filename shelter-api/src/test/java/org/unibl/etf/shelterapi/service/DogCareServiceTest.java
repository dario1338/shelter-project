package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.shelterapi.dto.CareRequest;
import org.unibl.etf.shelterapi.dto.CareResponse;
import org.unibl.etf.shelterapi.dto.VaccinationRequest;
import org.unibl.etf.shelterapi.exception.AccessForbiddenException;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.model.DogCare;
import org.unibl.etf.shelterapi.model.DogVaccination;
import org.unibl.etf.shelterapi.repository.DogCareRepository;
import org.unibl.etf.shelterapi.repository.DogRepository;
import org.unibl.etf.shelterapi.repository.SponsorshipApplicationRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DogCareServiceTest {

    private static final String ANA = "ana@example.com";
    private static final Long DOG = 5L;
    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);

    @Mock
    private DogCareRepository careRepository;

    @Mock
    private DogRepository dogRepository;

    @Mock
    private SponsorshipApplicationRepository applicationRepository;

    @InjectMocks
    private DogCareService careService;

    private static DogCare care() {
        DogCare care = new DogCare(DOG);
        care.replaceWith(true, "123456789", "Healthy", "Bites strangers",
                List.of(new DogVaccination(care, "Rabies", JAN_1, LocalDate.of(2027, 1, 1))));
        return care;
    }

    private static CareRequest request(List<VaccinationRequest> vaccinations) {
        return new CareRequest(true, "  123  ", "  Healthy ", "   ", vaccinations);
    }

    @Test
    void adminSeesEverythingIncludingTheInternalNote() {
        when(dogRepository.existsById(DOG)).thenReturn(true);
        when(careRepository.findById(DOG)).thenReturn(Optional.of(care()));

        CareResponse response = careService.getCare(DOG, "admin@example.com", true);

        assertEquals("Bites strangers", response.internalNote());
        assertEquals("Healthy", response.healthNotes());
        assertEquals(1, response.vaccinations().size());
        assertEquals("Rabies", response.vaccinations().get(0).name());
    }

    @Test
    void sponsorSeesTheCareDetailsWithoutTheInternalNote() {
        when(dogRepository.existsById(DOG)).thenReturn(true);
        when(applicationRepository.existsByUserEmailAndDogIdAndStatus(ANA, DOG, ApplicationStatus.APPROVED))
                .thenReturn(true);
        when(careRepository.findById(DOG)).thenReturn(Optional.of(care()));

        CareResponse response = careService.getCare(DOG, ANA, false);

        assertNull(response.internalNote());
        assertEquals("123456789", response.microchipNumber());
        assertEquals(Boolean.TRUE, response.sterilized());
    }

    @Test
    void userWhoIsNotTheSponsorIsRefusedAndNothingIsRead() {
        when(dogRepository.existsById(DOG)).thenReturn(true);
        when(applicationRepository.existsByUserEmailAndDogIdAndStatus(ANA, DOG, ApplicationStatus.APPROVED))
                .thenReturn(false);

        assertThrows(AccessForbiddenException.class, () -> careService.getCare(DOG, ANA, false));

        verify(careRepository, never()).findById(any());
    }

    @Test
    void unknownDogIsNotFound() {
        when(dogRepository.existsById(DOG)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> careService.getCare(DOG, ANA, true));
        assertThrows(ResourceNotFoundException.class, () -> careService.updateCare(DOG, request(null)));
    }

    @Test
    void dogWithoutCareDetailsGivesAnEmptyAnswer() {
        when(dogRepository.existsById(DOG)).thenReturn(true);
        when(careRepository.findById(DOG)).thenReturn(Optional.empty());

        CareResponse response = careService.getCare(DOG, "admin@example.com", true);

        assertNull(response.sterilized());
        assertNull(response.updatedAt());
        assertTrue(response.vaccinations().isEmpty());
    }

    @Test
    void updateCreatesTheDetailsAndCleansTheText() {
        when(dogRepository.existsById(DOG)).thenReturn(true);
        when(careRepository.findById(DOG)).thenReturn(Optional.empty());
        when(careRepository.save(any(DogCare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareResponse response = careService.updateCare(DOG, request(List.of(
                new VaccinationRequest("  Rabies ", JAN_1, null))));

        assertEquals("123", response.microchipNumber());
        assertEquals("Healthy", response.healthNotes());
        assertNull(response.internalNote());
        assertEquals("Rabies", response.vaccinations().get(0).name());
    }

    @Test
    void updateReplacesTheVaccinationsOfExistingDetails() {
        DogCare existing = care();
        when(dogRepository.existsById(DOG)).thenReturn(true);
        when(careRepository.findById(DOG)).thenReturn(Optional.of(existing));
        when(careRepository.save(any(DogCare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareResponse response = careService.updateCare(DOG, request(null));

        assertTrue(response.vaccinations().isEmpty());
        assertTrue(existing.getVaccinations().isEmpty());
    }

    @Test
    void updateRejectsANextDoseBeforeTheVaccination() {
        when(dogRepository.existsById(DOG)).thenReturn(true);
        CareRequest request = request(List.of(new VaccinationRequest("Rabies", JAN_1, JAN_1.minusDays(1))));

        assertThrows(InvalidRequestException.class, () -> careService.updateCare(DOG, request));

        verify(careRepository, never()).save(any(DogCare.class));
    }
}
