package org.unibl.etf.shelterapi.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.unibl.etf.shelterapi.dto.DogImageResponse;
import org.unibl.etf.shelterapi.service.DogImageService;

import java.io.IOException;
import java.net.URI;

/** Photos of a dog. They are shown through the dog endpoints; this controller only changes them (admin only). */
@RestController
@RequestMapping("/api/dogs/{dogId}/images")
public class DogImageController {

    private final DogImageService imageService;

    public DogImageController(DogImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DogImageResponse> upload(@PathVariable Long dogId,
                                                   @RequestParam("file") MultipartFile file) throws IOException {
        DogImageResponse created = imageService.upload(dogId, file.getBytes());
        return ResponseEntity.created(URI.create(created.url())).body(created);
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long dogId, @PathVariable Long imageId) {
        imageService.delete(dogId, imageId);
        return ResponseEntity.noContent().build();
    }
}
