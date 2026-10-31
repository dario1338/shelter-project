package org.unibl.etf.shelterapi.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.unibl.etf.shelterapi.dto.DogRequest;
import org.unibl.etf.shelterapi.dto.DogResponse;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.service.DogService;

import java.net.URI;

@RestController
@RequestMapping("/api/dogs")
public class DogController {

    private final DogService dogService;

    public DogController(DogService dogService) {
        this.dogService = dogService;
    }

    @GetMapping
    public PagedModel<DogResponse> getDogs(@RequestParam(required = false) DogStatus status, Pageable pageable) {
        return new PagedModel<>(dogService.getDogs(status, pageable));
    }

    @GetMapping("/{id}")
    public DogResponse getDog(@PathVariable Long id) {
        return dogService.getDog(id);
    }

    @PostMapping
    public ResponseEntity<DogResponse> createDog(@Valid @RequestBody DogRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        DogResponse created = dogService.createDog(request);
        URI location = uriBuilder.path("/api/dogs/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public DogResponse updateDog(@PathVariable Long id, @Valid @RequestBody DogRequest request) {
        return dogService.updateDog(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDog(@PathVariable Long id) {
        dogService.deleteDog(id);
        return ResponseEntity.noContent().build();
    }
}
