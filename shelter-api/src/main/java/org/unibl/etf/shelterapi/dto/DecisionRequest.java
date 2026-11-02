package org.unibl.etf.shelterapi.dto;

import jakarta.validation.constraints.Size;

public record DecisionRequest(
        @Size(max = 1000) String adminNote
) {
}
