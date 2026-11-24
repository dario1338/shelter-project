package org.unibl.etf.shelterapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// The day and the full hour of the visit in the shelter's local time. Visits start at 10:00 at the earliest and
// at 15:00 at the latest, and each takes one hour.
public record VisitRequest(
        @NotNull Long dogId,
        @NotNull LocalDate date,
        @NotNull @Min(10) @Max(15) Integer hour
) {
}
