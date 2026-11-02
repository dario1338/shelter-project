package org.unibl.etf.shelterapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.dto.SponsorshipRequest;
import org.unibl.etf.shelterapi.dto.SponsorshipResponse;
import org.unibl.etf.shelterapi.exception.ConflictException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.model.SponsorshipApplication;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.DogRepository;
import org.unibl.etf.shelterapi.repository.SponsorshipApplicationRepository;
import org.unibl.etf.shelterapi.repository.UserRepository;

@Service
@Transactional
public class SponsorshipService {

    private static final String AUTO_REJECT_NOTE = "Another application for this dog was approved";

    private final SponsorshipApplicationRepository applicationRepository;
    private final DogRepository dogRepository;
    private final UserRepository userRepository;

    public SponsorshipService(SponsorshipApplicationRepository applicationRepository,
                              DogRepository dogRepository,
                              UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.dogRepository = dogRepository;
        this.userRepository = userRepository;
    }

    public SponsorshipResponse apply(String email, SponsorshipRequest request) {
        User user = findUser(email);
        Dog dog = dogRepository.findById(request.dogId())
                .orElseThrow(() -> new ResourceNotFoundException("Dog with id " + request.dogId() + " not found"));
        if (dog.getStatus() != DogStatus.AVAILABLE) {
            throw new ConflictException("Dog is not available for sponsorship");
        }
        if (applicationRepository.existsByUserIdAndDogIdAndStatus(user.getId(), dog.getId(),
                ApplicationStatus.PENDING)) {
            throw new ConflictException("You already have a pending application for this dog");
        }
        SponsorshipApplication application = new SponsorshipApplication(user, dog, clean(request.note()));
        return toResponse(applicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public Page<SponsorshipResponse> getMyApplications(String email, Pageable pageable) {
        User user = findUser(email);
        return applicationRepository.findByUserId(user.getId(), pageable).map(SponsorshipService::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SponsorshipResponse> getApplications(ApplicationStatus status, Pageable pageable) {
        Page<SponsorshipApplication> applications = (status == null)
                ? applicationRepository.findAll(pageable)
                : applicationRepository.findByStatus(status, pageable);
        return applications.map(SponsorshipService::toResponse);
    }

    public SponsorshipResponse approve(Long id, String adminNote) {
        SponsorshipApplication application = findApplication(id);
        requirePending(application);
        Dog dog = application.getDog();
        if (dog.getStatus() != DogStatus.AVAILABLE) {
            throw new ConflictException("Dog is no longer available for sponsorship");
        }

        application.setStatus(ApplicationStatus.APPROVED);
        application.setAdminNote(clean(adminNote));
        dog.setStatus(DogStatus.SPONSORED);
        dogRepository.save(dog);

        for (SponsorshipApplication other : applicationRepository.findByDogIdAndStatus(dog.getId(),
                ApplicationStatus.PENDING)) {
            if (!other.getId().equals(application.getId())) {
                other.setStatus(ApplicationStatus.REJECTED);
                other.setAdminNote(AUTO_REJECT_NOTE);
            }
        }
        return toResponse(applicationRepository.save(application));
    }

    public SponsorshipResponse reject(Long id, String adminNote) {
        SponsorshipApplication application = findApplication(id);
        requirePending(application);
        application.setStatus(ApplicationStatus.REJECTED);
        application.setAdminNote(clean(adminNote));
        return toResponse(applicationRepository.save(application));
    }

    public SponsorshipResponse cancel(String email, Long id) {
        SponsorshipApplication application = findApplication(id);
        if (!application.getUser().getEmail().equals(email)) {
            // Do not reveal that somebody else's application exists.
            throw notFound(id);
        }
        requirePending(application);
        application.setStatus(ApplicationStatus.CANCELLED);
        return toResponse(applicationRepository.save(application));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private SponsorshipApplication findApplication(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Application with id " + id + " not found");
    }

    private static void requirePending(SponsorshipApplication application) {
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ConflictException("Only a pending application can be changed");
        }
    }

    private static String clean(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private static SponsorshipResponse toResponse(SponsorshipApplication application) {
        User user = application.getUser();
        Dog dog = application.getDog();
        return new SponsorshipResponse(
                application.getId(),
                dog.getId(),
                dog.getName(),
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                application.getStatus(),
                application.getUserNote(),
                application.getAdminNote(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
