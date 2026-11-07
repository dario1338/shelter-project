package org.unibl.etf.shelterapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The whole set of care details: an update replaces everything, including the vaccinations. */
public record CareRequest(
        Boolean sterilized,
        @Size(max = 30) String microchipNumber,
        @Size(max = 4000) String healthNotes,
        @Size(max = 4000) String internalNote,
        @Valid @Size(max = 30) List<@Valid VaccinationRequest> vaccinations
) {
}
