package org.unibl.etf.shelterapi.dto;

import org.unibl.etf.shelterapi.model.Role;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role
) {
}
