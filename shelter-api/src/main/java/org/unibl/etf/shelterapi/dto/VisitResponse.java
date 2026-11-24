package org.unibl.etf.shelterapi.dto;

import org.unibl.etf.shelterapi.model.VisitStatus;

import java.time.Instant;

public record VisitResponse(
        Long id,
        Long dogId,
        String dogName,
        Instant visitAt,
        VisitStatus status,
        Instant createdAt
) {
}
