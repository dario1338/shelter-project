package org.unibl.etf.shelterapi.dto;

import org.unibl.etf.shelterapi.model.ApplicationStatus;

import java.time.Instant;

public record SponsorshipResponse(
        Long id,
        Long dogId,
        String dogName,
        String applicantEmail,
        String applicantName,
        ApplicationStatus status,
        String userNote,
        String adminNote,
        Instant createdAt,
        Instant updatedAt
) {
}
