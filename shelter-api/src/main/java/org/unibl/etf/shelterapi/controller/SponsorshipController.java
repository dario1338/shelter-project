package org.unibl.etf.shelterapi.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unibl.etf.shelterapi.dto.DecisionRequest;
import org.unibl.etf.shelterapi.dto.SponsorshipRequest;
import org.unibl.etf.shelterapi.dto.SponsorshipResponse;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.service.SponsorshipService;

import java.security.Principal;

@RestController
@RequestMapping("/api/sponsorships")
public class SponsorshipController {

    private final SponsorshipService sponsorshipService;

    public SponsorshipController(SponsorshipService sponsorshipService) {
        this.sponsorshipService = sponsorshipService;
    }

    @PostMapping
    public ResponseEntity<SponsorshipResponse> apply(@Valid @RequestBody SponsorshipRequest request,
                                                     Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sponsorshipService.apply(principal.getName(), request));
    }

    @GetMapping("/me")
    public PagedModel<SponsorshipResponse> myApplications(
            Principal principal,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(sponsorshipService.getMyApplications(principal.getName(), pageable));
    }

    @GetMapping
    public PagedModel<SponsorshipResponse> applications(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(sponsorshipService.getApplications(status, pageable));
    }

    @PostMapping("/{id}/approve")
    public SponsorshipResponse approve(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) DecisionRequest request) {
        return sponsorshipService.approve(id, request == null ? null : request.adminNote());
    }

    @PostMapping("/{id}/reject")
    public SponsorshipResponse reject(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) DecisionRequest request) {
        return sponsorshipService.reject(id, request == null ? null : request.adminNote());
    }

    @PostMapping("/{id}/cancel")
    public SponsorshipResponse cancel(@PathVariable Long id, Principal principal) {
        return sponsorshipService.cancel(principal.getName(), id);
    }
}
