package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.unibl.etf.shelterapi.dto.VisitRequest;
import org.unibl.etf.shelterapi.dto.VisitResponse;
import org.unibl.etf.shelterapi.exception.ConflictException;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.model.DogVisit;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.model.VisitStatus;
import org.unibl.etf.shelterapi.repository.DogRepository;
import org.unibl.etf.shelterapi.repository.DogVisitRepository;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    private static final String ANA = "ana@example.com";
    // 24 November 2026, 13:00 in the shelter (UTC+1 in winter).
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Mock
    private DogVisitRepository visitRepository;

    @Mock
    private DogRepository dogRepository;

    @Mock
    private UserRepository userRepository;

    private VisitService visitService;

    @BeforeEach
    void setUp() {
        visitService = new VisitService(visitRepository, dogRepository, userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User ana() {
        User user = new User(ANA, "hashed", "Ana", "Anic", Role.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private static Dog dog(DogStatus status) {
        Dog dog = new Dog();
        dog.setId(5L);
        dog.setName("Rex");
        dog.setStatus(status);
        return dog;
    }

    private void givenAnaAndDog(DogStatus status) {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(ana()));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog(status)));
    }

    private Instant bookAndCaptureTime(LocalDate date, int hour) {
        when(visitRepository.save(any(DogVisit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        visitService.book(ANA, new VisitRequest(5L, date, hour));
        ArgumentCaptor<DogVisit> saved = ArgumentCaptor.forClass(DogVisit.class);
        verify(visitRepository).save(saved.capture());
        return saved.getValue().getVisitAt();
    }

    @Test
    void bookStoresTheShelterLocalTimeInWinter() {
        givenAnaAndDog(DogStatus.AVAILABLE);

        // 10:00 in Sarajevo is 09:00 UTC while Central European Time is in effect.
        assertEquals(Instant.parse("2026-12-01T09:00:00Z"), bookAndCaptureTime(LocalDate.of(2026, 12, 1), 10));
    }

    @Test
    void bookStoresTheShelterLocalTimeInSummer() {
        givenAnaAndDog(DogStatus.AVAILABLE);

        // During summer time the same local hour is 08:00 UTC.
        assertEquals(Instant.parse("2027-07-01T08:00:00Z"), bookAndCaptureTime(LocalDate.of(2027, 7, 1), 10));
    }

    @Test
    void bookReturnsTheBookedVisit() {
        givenAnaAndDog(DogStatus.SPONSORED);
        when(visitRepository.save(any(DogVisit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VisitResponse response = visitService.book(ANA, new VisitRequest(5L, LocalDate.of(2026, 12, 1), 10));

        assertEquals(5L, response.dogId());
        assertEquals("Rex", response.dogName());
        assertEquals(VisitStatus.BOOKED, response.status());
    }

    @Test
    void bookRejectsATimeThatHasPassed() {
        givenAnaAndDog(DogStatus.AVAILABLE);

        // Today at 10:00 in the shelter is already in the past at 13:00.
        VisitRequest request = new VisitRequest(5L, LocalDate.of(2026, 11, 24), 10);
        assertThrows(InvalidRequestException.class, () -> visitService.book(ANA, request));
        verify(visitRepository, never()).save(any());
    }

    @Test
    void bookRejectsATimeThatIsAlreadyBooked() {
        givenAnaAndDog(DogStatus.AVAILABLE);
        when(visitRepository.existsByDogIdAndVisitAtAndStatus(5L, Instant.parse("2026-12-01T09:00:00Z"),
                VisitStatus.BOOKED)).thenReturn(true);

        VisitRequest request = new VisitRequest(5L, LocalDate.of(2026, 12, 1), 10);
        assertThrows(ConflictException.class, () -> visitService.book(ANA, request));
        verify(visitRepository, never()).save(any());
    }

    @Test
    void bookRejectsADogThatWasAdopted() {
        givenAnaAndDog(DogStatus.ADOPTED);

        VisitRequest request = new VisitRequest(5L, LocalDate.of(2026, 12, 1), 10);
        assertThrows(ConflictException.class, () -> visitService.book(ANA, request));
    }

    @Test
    void bookThrowsWhenTheDogDoesNotExist() {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(ana()));
        when(dogRepository.findById(5L)).thenReturn(Optional.empty());

        VisitRequest request = new VisitRequest(5L, LocalDate.of(2026, 12, 1), 10);
        assertThrows(ResourceNotFoundException.class, () -> visitService.book(ANA, request));
    }

    @Test
    void bookThrowsWhenTheUserDoesNotExist() {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.empty());

        VisitRequest request = new VisitRequest(5L, LocalDate.of(2026, 12, 1), 10);
        assertThrows(ResourceNotFoundException.class, () -> visitService.book(ANA, request));
    }

    private static DogVisit visitOf(User user, Instant visitAt, VisitStatus status) {
        DogVisit visit = new DogVisit(dog(DogStatus.AVAILABLE), user, visitAt);
        ReflectionTestUtils.setField(visit, "id", 20L);
        visit.setStatus(status);
        return visit;
    }

    @Test
    void cancelMarksTheOwnFutureVisitCancelled() {
        DogVisit visit = visitOf(ana(), Instant.parse("2026-12-01T09:00:00Z"), VisitStatus.BOOKED);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        when(visitRepository.save(visit)).thenReturn(visit);

        assertEquals(VisitStatus.CANCELLED, visitService.cancel(ANA, 20L).status());
    }

    @Test
    void cancelDoesNotRevealTheVisitOfAnotherUser() {
        User bob = new User("bob@example.com", "hashed", "Bob", "Bobic", Role.USER);
        DogVisit visit = visitOf(bob, Instant.parse("2026-12-01T09:00:00Z"), VisitStatus.BOOKED);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));

        assertThrows(ResourceNotFoundException.class, () -> visitService.cancel(ANA, 20L));
        verify(visitRepository, never()).save(any());
    }

    @Test
    void cancelRefusesAVisitThatIsAlreadyCancelled() {
        DogVisit visit = visitOf(ana(), Instant.parse("2026-12-01T09:00:00Z"), VisitStatus.CANCELLED);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));

        assertThrows(ConflictException.class, () -> visitService.cancel(ANA, 20L));
    }

    @Test
    void cancelRefusesAVisitThatHasStarted() {
        DogVisit visit = visitOf(ana(), Instant.parse("2026-11-20T09:00:00Z"), VisitStatus.BOOKED);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));

        assertThrows(ConflictException.class, () -> visitService.cancel(ANA, 20L));
    }

    @Test
    void cancelThrowsWhenTheVisitDoesNotExist() {
        when(visitRepository.findById(20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> visitService.cancel(ANA, 20L));
    }

    @Test
    void myVisitsReturnsTheVisitsOfTheUser() {
        DogVisit visit = new DogVisit(dog(DogStatus.AVAILABLE), ana(), Instant.parse("2026-12-01T09:00:00Z"));
        when(visitRepository.findByUserEmail(ANA, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(visit)));

        List<VisitResponse> visits = visitService.getMyVisits(ANA, Pageable.unpaged()).getContent();

        assertEquals(1, visits.size());
        assertEquals("Rex", visits.get(0).dogName());
        assertEquals(Instant.parse("2026-12-01T09:00:00Z"), visits.get(0).visitAt());
    }
}
