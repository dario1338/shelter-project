package org.unibl.etf.shelterapi.dto;

import java.time.LocalDate;

public record VaccinationResponse(
        Long id,
        String name,
        LocalDate vaccinatedOn,
        LocalDate nextDueOn
) {
}
