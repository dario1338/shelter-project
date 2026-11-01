package org.unibl.etf.shelterapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName
) {
}
