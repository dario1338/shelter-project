package org.unibl.etf.shelterapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.dto.DogRequest;
import org.unibl.etf.shelterapi.dto.DogResponse;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.repository.DogRepository;

@Service
@Transactional
public class DogService {

    private final DogRepository dogRepository;

    public DogService(DogRepository dogRepository) {
        this.dogRepository = dogRepository;
    }

    @Transactional(readOnly = true)
    public Page<DogResponse> getDogs(DogStatus status, Pageable pageable) {
        Page<Dog> dogs = (status == null)
                ? dogRepository.findAll(pageable)
                : dogRepository.findByStatus(status, pageable);
        return dogs.map(DogService::toResponse);
    }

    @Transactional(readOnly = true)
    public DogResponse getDog(Long id) {
        return toResponse(findOrThrow(id));
    }

    public DogResponse createDog(DogRequest request) {
        Dog dog = new Dog();
        apply(dog, request);
        if (request.status() == null) {
            dog.setStatus(DogStatus.AVAILABLE);
        }
        return toResponse(dogRepository.save(dog));
    }

    public DogResponse updateDog(Long id, DogRequest request) {
        Dog dog = findOrThrow(id);
        apply(dog, request);
        return toResponse(dogRepository.save(dog));
    }

    public void deleteDog(Long id) {
        dogRepository.delete(findOrThrow(id));
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

    private static DogResponse toResponse(Dog dog) {
        return new DogResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getBirthYear(),
                dog.getDescription(),
                dog.getStatus()
        );
    }
}
