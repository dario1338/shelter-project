package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.unibl.etf.shelterapi.dto.DogImageResponse;
import org.unibl.etf.shelterapi.exception.ConflictException;
import org.unibl.etf.shelterapi.exception.ImageStorageException;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.exception.UploadTooLargeException;
import org.unibl.etf.shelterapi.model.DogImage;
import org.unibl.etf.shelterapi.repository.DogImageRepository;
import org.unibl.etf.shelterapi.repository.DogRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DogImageServiceTest {

    private static final byte[] UPLOAD = {1, 2, 3};
    private static final byte[] PROCESSED = {9, 9};

    @Mock
    private DogRepository dogRepository;

    @Mock
    private DogImageRepository imageRepository;

    @Mock
    private ImageProcessor processor;

    @Mock
    private ImageStorage storage;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private DogImageService service;

    private void dogExists() {
        when(dogRepository.existsById(1L)).thenReturn(true);
    }

    private void processingSucceeds() {
        when(processor.process(UPLOAD)).thenReturn(new ProcessedImage(PROCESSED, "image/jpeg", "jpg"));
    }

    @Test
    void uploadStoresTheProcessedFileAndSavesTheRow() {
        dogExists();
        processingSucceeds();
        when(storage.publicUrl(anyString())).thenAnswer(invocation -> "https://files/" + invocation.getArgument(0));
        when(imageRepository.countByDogId(1L)).thenReturn(2L);
        when(imageRepository.findMaxSortOrder(1L)).thenReturn(2);
        when(imageRepository.saveAndFlush(any(DogImage.class))).thenAnswer(invocation -> {
            DogImage image = invocation.getArgument(0);
            image.setId(7L);
            return image;
        });

        DogImageResponse result = service.upload(1L, UPLOAD);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), eq(PROCESSED), eq("image/jpeg"));
        assertTrue(key.getValue().matches("dogs/1/[0-9a-f-]{36}\\.jpg"));
        ArgumentCaptor<DogImage> saved = ArgumentCaptor.forClass(DogImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        assertEquals(1L, saved.getValue().getDogId());
        assertEquals(key.getValue(), saved.getValue().getStorageKey());
        assertEquals("image/jpeg", saved.getValue().getContentType());
        assertEquals(3, saved.getValue().getSortOrder());
        assertEquals(7L, result.id());
        assertEquals("https://files/" + key.getValue(), result.url());
    }

    @Test
    void uploadForAnUnknownDogIsNotFound() {
        when(dogRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.upload(99L, UPLOAD));
        verifyNoInteractions(processor, storage);
    }

    @Test
    void emptyUploadIsRejected() {
        dogExists();
        byte[] empty = new byte[0];

        assertThrows(InvalidRequestException.class, () -> service.upload(1L, empty));
        verifyNoInteractions(processor, storage);
    }

    @Test
    void tooBigUploadIsRejected() {
        dogExists();
        byte[] big = new byte[(int) DogImageService.MAX_UPLOAD_BYTES + 1];

        assertThrows(UploadTooLargeException.class, () -> service.upload(1L, big));
        verifyNoInteractions(processor, storage);
    }

    @Test
    void dogCannotHaveMoreThanTheAllowedNumberOfImages() {
        dogExists();
        when(imageRepository.countByDogId(1L)).thenReturn((long) DogImageService.MAX_IMAGES_PER_DOG);

        assertThrows(ConflictException.class, () -> service.upload(1L, UPLOAD));
        verifyNoInteractions(processor, storage);
    }

    @Test
    void invalidImageIsNeverStored() {
        dogExists();
        when(imageRepository.countByDogId(1L)).thenReturn(0L);
        when(processor.process(UPLOAD)).thenThrow(new InvalidRequestException("The file is not a valid JPEG or PNG image"));

        assertThrows(InvalidRequestException.class, () -> service.upload(1L, UPLOAD));
        verifyNoInteractions(storage);
        verify(imageRepository, never()).saveAndFlush(any(DogImage.class));
    }

    @Test
    void storageFailureLeavesNoRowBehind() {
        dogExists();
        processingSucceeds();
        when(imageRepository.countByDogId(1L)).thenReturn(0L);
        doThrow(new ImageStorageException("down")).when(storage).store(anyString(), any(byte[].class), anyString());

        assertThrows(ImageStorageException.class, () -> service.upload(1L, UPLOAD));
        verify(imageRepository, never()).saveAndFlush(any(DogImage.class));
    }

    @Test
    void failedSaveRemovesTheStoredFile() {
        dogExists();
        processingSucceeds();
        when(imageRepository.countByDogId(1L)).thenReturn(0L);
        when(imageRepository.findMaxSortOrder(1L)).thenReturn(0);
        when(imageRepository.saveAndFlush(any(DogImage.class))).thenThrow(new DataIntegrityViolationException("x"));

        assertThrows(DataIntegrityViolationException.class, () -> service.upload(1L, UPLOAD));

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(storage).store(stored.capture(), any(byte[].class), anyString());
        verify(storage).delete(stored.getValue());
    }

    @Test
    void failedCleanupAfterAFailedSaveDoesNotHideTheOriginalError() {
        dogExists();
        processingSucceeds();
        when(imageRepository.countByDogId(1L)).thenReturn(0L);
        when(imageRepository.findMaxSortOrder(1L)).thenReturn(0);
        when(imageRepository.saveAndFlush(any(DogImage.class))).thenThrow(new DataIntegrityViolationException("x"));
        doThrow(new ImageStorageException("down")).when(storage).delete(anyString());

        assertThrows(DataIntegrityViolationException.class, () -> service.upload(1L, UPLOAD));
    }

    @Test
    void deleteRemovesTheRowAndAnnouncesTheFile() {
        DogImage image = new DogImage();
        image.setId(5L);
        image.setDogId(1L);
        image.setStorageKey("dogs/1/a.jpg");
        when(imageRepository.findByIdAndDogId(5L, 1L)).thenReturn(Optional.of(image));

        service.delete(1L, 5L);

        verify(imageRepository).delete(image);
        ArgumentCaptor<ImageFilesToRemove> event = ArgumentCaptor.forClass(ImageFilesToRemove.class);
        verify(events).publishEvent(event.capture());
        assertEquals(List.of("dogs/1/a.jpg"), event.getValue().storageKeys());
    }

    @Test
    void deleteOfAnImageThatDoesNotBelongToTheDogIsNotFound() {
        when(imageRepository.findByIdAndDogId(5L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(2L, 5L));
        verify(imageRepository, never()).delete(any(DogImage.class));
        verifyNoInteractions(events);
    }
}
