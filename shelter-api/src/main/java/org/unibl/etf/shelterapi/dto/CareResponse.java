package org.unibl.etf.shelterapi.dto;

import java.time.Instant;
import java.util.List;

/** internalNote is filled for administrators only and is null for everybody else. */
public record CareResponse(
        Boolean sterilized,
        String microchipNumber,
        String healthNotes,
        String internalNote,
        List<VaccinationResponse> vaccinations,
        Instant updatedAt
) {
}
