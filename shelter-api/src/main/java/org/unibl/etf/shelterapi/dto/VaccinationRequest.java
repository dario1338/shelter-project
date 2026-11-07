package org.unibl.etf.shelterapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VaccinationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate vaccinatedOn,
        LocalDate nextDueOn
) {
}
