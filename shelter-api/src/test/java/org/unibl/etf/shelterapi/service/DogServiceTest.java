package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.unibl.etf.shelterapi.dto.DogRequest;
import org.unibl.etf.shelterapi.dto.DogResponse;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.repository.DogRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DogServiceTest {

    @Mock
    private DogRepository dogRepository;

    @InjectMocks
    private DogService dogService;

    private static Dog dog(Long id, String name, DogStatus status) {
        Dog dog = new Dog();
        dog.setId(id);
        dog.setName(name);
        dog.setBreed("Mixed");
        dog.setBirthYear(2020);
        dog.setDescription("Friendly");
        dog.setStatus(status);
        return dog;
    }

    @Test
    void getDogsWithoutStatusReturnsAllDogs() {
        Pageable pageable = PageRequest.of(0, 10);
        when(dogRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(dog(1L, "Rex", DogStatus.AVAILABLE))));

        Page<DogResponse> result = dogService.getDogs(null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Rex", result.getContent().get(0).name());
    }

    @Test
    void getDogsWithStatusFiltersByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        when(dogRepository.findByStatus(DogStatus.SPONSORED, pageable))
                .thenReturn(new PageImpl<>(List.of(dog(2L, "Luna", DogStatus.SPONSORED))));

        Page<DogResponse> result = dogService.getDogs(DogStatus.SPONSORED, pageable);

        assertEquals(DogStatus.SPONSORED, result.getContent().get(0).status());
        verify(dogRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getDogReturnsDogWhenFound() {
        when(dogRepository.findById(1L)).thenReturn(Optional.of(dog(1L, "Rex", DogStatus.AVAILABLE)));

        DogResponse result = dogService.getDog(1L);

        assertEquals(1L, result.id());
        assertEquals("Rex", result.name());
    }

    @Test
    void getDogThrowsWhenNotFound() {
        when(dogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dogService.getDog(99L));
    }

    @Test
    void createDogDefaultsStatusToAvailable() {
        when(dogRepository.save(any(Dog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DogResponse result = dogService.createDog(new DogRequest("Rex", "Mixed", 2020, "Friendly", null));

        assertEquals(DogStatus.AVAILABLE, result.status());
        assertEquals("Rex", result.name());
    }

    @Test
    void createDogKeepsExplicitStatus() {
        when(dogRepository.save(any(Dog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DogResponse result = dogService.createDog(new DogRequest("Rex", null, null, null, DogStatus.ADOPTED));

        assertEquals(DogStatus.ADOPTED, result.status());
    }

    @Test
    void updateDogChangesFields() {
        Dog existing = dog(1L, "Rex", DogStatus.AVAILABLE);
        when(dogRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dogRepository.save(any(Dog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DogResponse result = dogService.updateDog(1L,
                new DogRequest("Max", "Beagle", 2019, "Calm", DogStatus.SPONSORED));

        assertEquals("Max", result.name());
        assertEquals("Beagle", result.breed());
        assertEquals(DogStatus.SPONSORED, result.status());
    }

    @Test
    void updateDogWithoutStatusKeepsCurrentStatus() {
        Dog existing = dog(1L, "Rex", DogStatus.SPONSORED);
        when(dogRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dogRepository.save(any(Dog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DogResponse result = dogService.updateDog(1L, new DogRequest("Rex", null, null, null, null));

        assertEquals(DogStatus.SPONSORED, result.status());
    }

    @Test
    void updateDogThrowsWhenNotFound() {
        when(dogRepository.findById(99L)).thenReturn(Optional.empty());
        DogRequest request = new DogRequest("Rex", null, null, null, null);

        assertThrows(ResourceNotFoundException.class, () -> dogService.updateDog(99L, request));
        verify(dogRepository, never()).save(any(Dog.class));
    }

    @Test
    void deleteDogRemovesExistingDog() {
        Dog existing = dog(1L, "Rex", DogStatus.AVAILABLE);
        when(dogRepository.findById(1L)).thenReturn(Optional.of(existing));

        dogService.deleteDog(1L);

        ArgumentCaptor<Dog> captor = ArgumentCaptor.forClass(Dog.class);
        verify(dogRepository).delete(captor.capture());
        assertEquals(1L, captor.getValue().getId());
    }

    @Test
    void deleteDogThrowsWhenNotFound() {
        when(dogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dogService.deleteDog(99L));
        verify(dogRepository, never()).delete(any(Dog.class));
    }
}
