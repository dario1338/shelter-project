package org.unibl.etf.shelterapi.dto;

import org.unibl.etf.shelterapi.model.DogStatus;

public record DogResponse(
        Long id,
        String name,
        String breed,
        Integer birthYear,
        String description,
        DogStatus status
) {
}
