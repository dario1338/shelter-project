package org.unibl.etf.shelterapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unibl.etf.shelterapi.model.DogStatus;

public record DogRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 50) String breed,
        @Min(1990) @Max(2100) Integer birthYear,
        @Size(max = 2000) String description,
        DogStatus status
) {
}
