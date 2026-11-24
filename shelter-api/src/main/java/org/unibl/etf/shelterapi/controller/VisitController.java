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
import org.springframework.web.bind.annotation.RestController;
import org.unibl.etf.shelterapi.dto.VisitRequest;
import org.unibl.etf.shelterapi.dto.VisitResponse;
import org.unibl.etf.shelterapi.service.VisitService;

import java.security.Principal;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PostMapping
    public ResponseEntity<VisitResponse> book(@Valid @RequestBody VisitRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(visitService.book(principal.getName(), request));
    }

    @PostMapping("/{id}/cancel")
    public VisitResponse cancel(@PathVariable Long id, Principal principal) {
        return visitService.cancel(principal.getName(), id);
    }

    @GetMapping("/me")
    public PagedModel<VisitResponse> myVisits(
            Principal principal,
            @PageableDefault(sort = "visitAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(visitService.getMyVisits(principal.getName(), pageable));
    }
}
