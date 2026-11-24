package org.unibl.etf.shelterapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.dto.VisitRequest;
import org.unibl.etf.shelterapi.dto.VisitResponse;
import org.unibl.etf.shelterapi.exception.ConflictException;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.model.DogVisit;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.model.VisitStatus;
import org.unibl.etf.shelterapi.repository.DogRepository;
import org.unibl.etf.shelterapi.repository.DogVisitRepository;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Service
@Transactional
public class VisitService {

    // Visit times are the shelter's local times, whatever the time zone of the server.
    public static final ZoneId SHELTER_ZONE = ZoneId.of("Europe/Sarajevo");

    private final DogVisitRepository visitRepository;
    private final DogRepository dogRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public VisitService(DogVisitRepository visitRepository,
                        DogRepository dogRepository,
                        UserRepository userRepository,
                        Clock clock) {
        this.visitRepository = visitRepository;
        this.dogRepository = dogRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public VisitResponse book(String email, VisitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Dog dog = dogRepository.findById(request.dogId())
                .orElseThrow(() -> new ResourceNotFoundException("Dog with id " + request.dogId() + " not found"));
        if (dog.getStatus() == DogStatus.ADOPTED) {
            throw new ConflictException("The dog is no longer in the shelter");
        }
        Instant visitAt = request.date().atTime(request.hour(), 0).atZone(SHELTER_ZONE).toInstant();
        if (!visitAt.isAfter(clock.instant())) {
            throw new InvalidRequestException("A visit can only be booked for a time in the future");
        }
        // The unique index in the database is the final guard when two users book the same time at once.
        if (visitRepository.existsByDogIdAndVisitAtAndStatus(dog.getId(), visitAt, VisitStatus.BOOKED)) {
            throw new ConflictException("This time is already booked for this dog");
        }
        return toResponse(visitRepository.save(new DogVisit(dog, user, visitAt)));
    }

    // A user cancels their own visit while it is still ahead; the time becomes free for others again.
    public VisitResponse cancel(String email, Long id) {
        DogVisit visit = visitRepository.findById(id).orElseThrow(() -> notFound(id));
        if (!visit.getUser().getEmail().equals(email)) {
            // Do not reveal that somebody else's visit exists.
            throw notFound(id);
        }
        if (visit.getStatus() != VisitStatus.BOOKED) {
            throw new ConflictException("Only a booked visit can be cancelled");
        }
        if (!visit.getVisitAt().isAfter(clock.instant())) {
            throw new ConflictException("A visit that has already started cannot be cancelled");
        }
        visit.setStatus(VisitStatus.CANCELLED);
        return toResponse(visitRepository.save(visit));
    }

    @Transactional(readOnly = true)
    public Page<VisitResponse> getMyVisits(String email, Pageable pageable) {
        return visitRepository.findByUserEmail(email, pageable).map(VisitService::toResponse);
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Visit with id " + id + " not found");
    }

    private static VisitResponse toResponse(DogVisit visit) {
        Dog dog = visit.getDog();
        return new VisitResponse(
                visit.getId(),
                dog.getId(),
                dog.getName(),
                visit.getVisitAt(),
                visit.getStatus(),
                visit.getCreatedAt());
    }
}
