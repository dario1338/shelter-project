package org.unibl.etf.shelterapi.dto;

import org.unibl.etf.shelterapi.model.DogStatus;

import java.util.List;

/**
 * primaryImageUrl is the address of the first photo, or null when the dog has none.
 * images lists all photos in display order.
 */
public record DogResponse(
        Long id,
        String name,
        String breed,
        Integer birthYear,
        String description,
        DogStatus status,
        String primaryImageUrl,
        List<DogImageResponse> images
) {
}
