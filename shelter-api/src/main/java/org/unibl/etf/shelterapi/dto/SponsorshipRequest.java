package org.unibl.etf.shelterapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SponsorshipRequest(
        @NotNull Long dogId,
        @Size(max = 1000) String note
) {
}
