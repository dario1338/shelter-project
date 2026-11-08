package org.unibl.etf.shelterapi.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.dto.DogImageResponse;
import org.unibl.etf.shelterapi.dto.DogRequest;
import org.unibl.etf.shelterapi.dto.DogResponse;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogImage;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.repository.DogImageRepository;
import org.unibl.etf.shelterapi.repository.DogRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class DogService {

    private final DogRepository dogRepository;
    private final DogImageRepository imageRepository;
    private final ImageStorage storage;
    private final ApplicationEventPublisher events;

    public DogService(DogRepository dogRepository, DogImageRepository imageRepository,
                      ImageStorage storage, ApplicationEventPublisher events) {
        this.dogRepository = dogRepository;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<DogResponse> getDogs(DogStatus status, Pageable pageable) {
        Page<Dog> dogs = (status == null)
                ? dogRepository.findAll(pageable)
                : dogRepository.findByStatus(status, pageable);
        Map<Long, List<DogImage>> images = imagesByDog(dogs.map(Dog::getId).getContent());
        return dogs.map(dog -> toResponse(dog, images.getOrDefault(dog.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public DogResponse getDog(Long id) {
        Dog dog = findOrThrow(id);
        return toResponse(dog, imageRepository.findByDogIdOrderBySortOrderAscIdAsc(dog.getId()));
    }

    public DogResponse createDog(DogRequest request) {
        Dog dog = new Dog();
        apply(dog, request);
        if (request.status() == null) {
            dog.setStatus(DogStatus.AVAILABLE);
        }
        return toResponse(dogRepository.save(dog), List.of());
    }

    public DogResponse updateDog(Long id, DogRequest request) {
        Dog dog = findOrThrow(id);
        apply(dog, request);
        Dog saved = dogRepository.save(dog);
        return toResponse(saved, imageRepository.findByDogIdOrderBySortOrderAscIdAsc(saved.getId()));
    }

    /** The photos are removed from the database with the dog; their files are deleted after the commit. */
    public void deleteDog(Long id) {
        Dog dog = findOrThrow(id);
        List<String> keys = imageRepository.findByDogIdOrderBySortOrderAscIdAsc(dog.getId()).stream()
                .map(DogImage::getStorageKey)
                .toList();
        dogRepository.delete(dog);
        if (!keys.isEmpty()) {
            events.publishEvent(new ImageFilesToRemove(keys));
        }
    }

    private Map<Long, List<DogImage>> imagesByDog(List<Long> dogIds) {
        if (dogIds.isEmpty()) {
            return Map.of();
        }
        return imageRepository.findByDogIdInOrderBySortOrderAscIdAsc(dogIds).stream()
                .collect(Collectors.groupingBy(DogImage::getDogId));
    }

    private Dog findOrThrow(Long id) {
        return dogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dog with id " + id + " not found"));
    }

    private static void apply(Dog dog, DogRequest request) {
        dog.setName(request.name());
        dog.setBreed(request.breed());
        dog.setBirthYear(request.birthYear());
        dog.setDescription(request.description());
        if (request.status() != null) {
            dog.setStatus(request.status());
        }
    }

    private DogResponse toResponse(Dog dog, List<DogImage> images) {
        List<DogImageResponse> imageResponses = images.stream()
                .map(image -> new DogImageResponse(image.getId(), storage.publicUrl(image.getStorageKey())))
                .toList();
        return new DogResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getBirthYear(),
                dog.getDescription(),
                dog.getStatus(),
                imageResponses.isEmpty() ? null : imageResponses.get(0).url(),
                imageResponses
        );
    }
}
