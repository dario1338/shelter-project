package org.unibl.etf.shelterapi.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unibl.etf.shelterapi.dto.CareRequest;
import org.unibl.etf.shelterapi.dto.CareResponse;
import org.unibl.etf.shelterapi.service.DogCareService;

@RestController
@RequestMapping("/api/dogs/{id}/care")
public class DogCareController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final DogCareService careService;

    public DogCareController(DogCareService careService) {
        this.careService = careService;
    }

    @GetMapping
    public CareResponse getCare(@PathVariable Long id, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return careService.getCare(id, authentication.getName(), admin);
    }

    @PutMapping
    public CareResponse updateCare(@PathVariable Long id, @Valid @RequestBody CareRequest request) {
        return careService.updateCare(id, request);
    }
}
