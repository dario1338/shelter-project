package org.unibl.etf.shelterapi.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.dto.CareRequest;
import org.unibl.etf.shelterapi.dto.CareResponse;
import org.unibl.etf.shelterapi.dto.VaccinationRequest;
import org.unibl.etf.shelterapi.dto.VaccinationResponse;
import org.unibl.etf.shelterapi.exception.AccessForbiddenException;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.model.DogCare;
import org.unibl.etf.shelterapi.model.DogVaccination;
import org.unibl.etf.shelterapi.repository.DogCareRepository;
import org.unibl.etf.shelterapi.repository.DogRepository;
import org.unibl.etf.shelterapi.repository.SponsorshipApplicationRepository;

import java.util.List;

@Service
@Transactional
public class DogCareService {

    private final DogCareRepository careRepository;
    private final DogRepository dogRepository;
    private final SponsorshipApplicationRepository applicationRepository;

    public DogCareService(DogCareRepository careRepository,
                          DogRepository dogRepository,
                          SponsorshipApplicationRepository applicationRepository) {
        this.careRepository = careRepository;
        this.dogRepository = dogRepository;
        this.applicationRepository = applicationRepository;
    }

    /**
     * Administrators see everything. A user sees the care details only of a dog that he sponsors, which means that
     * his application for that dog was approved (it stays approved, also when the dog is adopted later).
     */
    @Transactional(readOnly = true)
    public CareResponse getCare(Long dogId, String email, boolean admin) {
        requireDog(dogId);
        if (!admin && !applicationRepository.existsByUserEmailAndDogIdAndStatus(email, dogId,
                ApplicationStatus.APPROVED)) {
            throw new AccessForbiddenException("Only the sponsor of this dog and administrators can see its care details");
        }
        return careRepository.findById(dogId)
                .map(care -> toResponse(care, admin))
                .orElseGet(DogCareService::emptyResponse);
    }

    public CareResponse updateCare(Long dogId, CareRequest request) {
        requireDog(dogId);
        List<VaccinationRequest> requested = request.vaccinations() == null ? List.of() : request.vaccinations();
        for (VaccinationRequest vaccination : requested) {
            if (vaccination.nextDueOn() != null && vaccination.nextDueOn().isBefore(vaccination.vaccinatedOn())) {
                throw new InvalidRequestException("The next dose of " + vaccination.name().trim()
                        + " cannot be due before the vaccination date");
            }
        }

        DogCare care = careRepository.findById(dogId).orElseGet(() -> new DogCare(dogId));
        List<DogVaccination> vaccinations = requested.stream()
                .map(v -> new DogVaccination(care, v.name().trim(), v.vaccinatedOn(), v.nextDueOn()))
                .toList();
        care.replaceWith(request.sterilized(), clean(request.microchipNumber()), clean(request.healthNotes()),
                clean(request.internalNote()), vaccinations);
        return toResponse(careRepository.save(care), true);
    }

    private void requireDog(Long dogId) {
        if (!dogRepository.existsById(dogId)) {
            throw new ResourceNotFoundException("Dog with id " + dogId + " not found");
        }
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static CareResponse emptyResponse() {
        return new CareResponse(null, null, null, null, List.of(), null);
    }

    private static CareResponse toResponse(DogCare care, boolean admin) {
        List<VaccinationResponse> vaccinations = care.getVaccinations().stream()
                .map(v -> new VaccinationResponse(v.getId(), v.getName(), v.getVaccinatedOn(), v.getNextDueOn()))
                .toList();
        return new CareResponse(
                care.getSterilized(),
                care.getMicrochipNumber(),
                care.getHealthNotes(),
                admin ? care.getInternalNote() : null,
                vaccinations,
                care.getUpdatedAt());
    }
}
