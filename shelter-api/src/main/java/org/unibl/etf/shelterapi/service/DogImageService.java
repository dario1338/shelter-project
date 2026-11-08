package org.unibl.etf.shelterapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.UUID;

@Service
@Transactional
public class DogImageService {

    /** Keep in step with spring.servlet.multipart.max-file-size, which stops bigger uploads earlier. */
    static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    static final int MAX_IMAGES_PER_DOG = 6;

    private static final Logger LOG = LoggerFactory.getLogger(DogImageService.class);

    private final DogRepository dogRepository;
    private final DogImageRepository imageRepository;
    private final ImageProcessor processor;
    private final ImageStorage storage;
    private final ApplicationEventPublisher events;

    public DogImageService(DogRepository dogRepository, DogImageRepository imageRepository,
                           ImageProcessor processor, ImageStorage storage, ApplicationEventPublisher events) {
        this.dogRepository = dogRepository;
        this.imageRepository = imageRepository;
        this.processor = processor;
        this.storage = storage;
        this.events = events;
    }

    public DogImageResponse upload(Long dogId, byte[] content) {
        if (!dogRepository.existsById(dogId)) {
            throw new ResourceNotFoundException("Dog with id " + dogId + " not found");
        }
        if (content.length == 0) {
            throw new InvalidRequestException("The file is empty");
        }
        if (content.length > MAX_UPLOAD_BYTES) {
            throw new UploadTooLargeException("The image is too large (at most 5 MB)");
        }
        if (imageRepository.countByDogId(dogId) >= MAX_IMAGES_PER_DOG) {
            throw new ConflictException("A dog can have at most " + MAX_IMAGES_PER_DOG + " images");
        }

        ProcessedImage image = processor.process(content);
        String key = "dogs/" + dogId + "/" + UUID.randomUUID() + "." + image.extension();
        storage.store(key, image.content(), image.contentType());

        DogImage entity = new DogImage();
        entity.setDogId(dogId);
        entity.setStorageKey(key);
        entity.setContentType(image.contentType());
        entity.setSortOrder(imageRepository.findMaxSortOrder(dogId) + 1);
        try {
            DogImage saved = imageRepository.saveAndFlush(entity);
            return new DogImageResponse(saved.getId(), storage.publicUrl(key));
        } catch (RuntimeException e) {
            removeFileQuietly(key);
            throw e;
        }
    }

    public void delete(Long dogId, Long imageId) {
        DogImage image = imageRepository.findByIdAndDogId(imageId, dogId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image with id " + imageId + " not found for dog " + dogId));
        imageRepository.delete(image);
        events.publishEvent(new ImageFilesToRemove(List.of(image.getStorageKey())));
    }

    /** The database row could not be saved, so the file that was just stored must not stay behind. */
    private void removeFileQuietly(String key) {
        try {
            storage.delete(key);
        } catch (ImageStorageException e) {
            LOG.warn("Could not remove image file {} after a failed save: {}", key, e.getMessage());
        }
    }
}
