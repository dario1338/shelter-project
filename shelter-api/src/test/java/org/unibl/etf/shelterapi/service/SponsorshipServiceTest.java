package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.unibl.etf.shelterapi.dto.SponsorshipRequest;
import org.unibl.etf.shelterapi.dto.SponsorshipResponse;
import org.unibl.etf.shelterapi.exception.ConflictException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.SponsorshipApplication;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.DogRepository;
import org.unibl.etf.shelterapi.repository.SponsorshipApplicationRepository;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorshipServiceTest {

    private static final String ANA = "ana@example.com";
    private static final String BOB = "bob@example.com";

    @Mock
    private SponsorshipApplicationRepository applicationRepository;

    @Mock
    private DogRepository dogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SponsorshipService sponsorshipService;

    private static User user(Long id, String email) {
        User user = new User(email, "hashed", "Ana", "Anic", Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Dog dog(Long id, DogStatus status) {
        Dog dog = new Dog();
        dog.setId(id);
        dog.setName("Rex");
        dog.setStatus(status);
        return dog;
    }

    private static SponsorshipApplication application(Long id, User user, Dog dog, ApplicationStatus status) {
        SponsorshipApplication application = new SponsorshipApplication(user, dog, "note");
        ReflectionTestUtils.setField(application, "id", id);
        application.setStatus(status);
        return application;
    }

    @Test
    void applyCreatesPendingApplication() {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(user(1L, ANA)));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog(5L, DogStatus.AVAILABLE)));
        when(applicationRepository.existsByUserIdAndDogIdAndStatus(1L, 5L, ApplicationStatus.PENDING))
                .thenReturn(false);
        when(applicationRepository.save(any(SponsorshipApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SponsorshipResponse response = sponsorshipService.apply(ANA, new SponsorshipRequest(5L, "  I love him "));

        assertEquals(ApplicationStatus.PENDING, response.status());
        assertEquals("Rex", response.dogName());
        assertEquals("I love him", response.userNote());
        assertEquals(ANA, response.applicantEmail());
        assertEquals("Ana Anic", response.applicantName());
    }

    @Test
    void applyThrowsWhenUserIsUnknown() {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.empty());
        SponsorshipRequest request = new SponsorshipRequest(5L, null);

        assertThrows(ResourceNotFoundException.class, () -> sponsorshipService.apply(ANA, request));
        verifyNoInteractions(dogRepository, applicationRepository);
    }

    @Test
    void applyThrowsWhenDogIsUnknown() {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(user(1L, ANA)));
        when(dogRepository.findById(9L)).thenReturn(Optional.empty());
        SponsorshipRequest request = new SponsorshipRequest(9L, null);

        assertThrows(ResourceNotFoundException.class, () -> sponsorshipService.apply(ANA, request));
        verify(applicationRepository, never()).save(any(SponsorshipApplication.class));
    }

    @Test
    void applyRejectsDogThatIsNotAvailable() {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(user(1L, ANA)));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog(5L, DogStatus.SPONSORED)));
        SponsorshipRequest request = new SponsorshipRequest(5L, null);

        assertThrows(ConflictException.class, () -> sponsorshipService.apply(ANA, request));
        verify(applicationRepository, never()).save(any(SponsorshipApplication.class));
    }

    @Test
    void applyRejectsSecondPendingApplicationForTheSameDog() {
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(user(1L, ANA)));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog(5L, DogStatus.AVAILABLE)));
        when(applicationRepository.existsByUserIdAndDogIdAndStatus(1L, 5L, ApplicationStatus.PENDING))
                .thenReturn(true);
        SponsorshipRequest request = new SponsorshipRequest(5L, null);

        assertThrows(ConflictException.class, () -> sponsorshipService.apply(ANA, request));
        verify(applicationRepository, never()).save(any(SponsorshipApplication.class));
    }

    @Test
    void myApplicationsReturnsOnlyTheUsersApplications() {
        Pageable pageable = PageRequest.of(0, 10);
        User ana = user(1L, ANA);
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(ana));
        when(applicationRepository.findByUserId(1L, pageable)).thenReturn(new PageImpl<>(List.of(
                application(10L, ana, dog(5L, DogStatus.AVAILABLE), ApplicationStatus.PENDING))));

        Page<SponsorshipResponse> result = sponsorshipService.getMyApplications(ANA, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(ApplicationStatus.PENDING, result.getContent().get(0).status());
    }

    @Test
    void myApplicationsCanBeFilteredByDog() {
        Pageable pageable = PageRequest.of(0, 10);
        User ana = user(1L, ANA);
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(ana));
        when(applicationRepository.findByUserIdAndDogId(1L, 5L, pageable)).thenReturn(new PageImpl<>(List.of(
                application(10L, ana, dog(5L, DogStatus.AVAILABLE), ApplicationStatus.PENDING))));

        Page<SponsorshipResponse> result = sponsorshipService.getMyApplications(ANA, 5L, null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void myApplicationsCanBeFilteredByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        User ana = user(1L, ANA);
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(ana));
        when(applicationRepository.findByUserIdAndStatus(1L, ApplicationStatus.APPROVED, pageable))
                .thenReturn(new PageImpl<>(List.of(
                        application(10L, ana, dog(5L, DogStatus.SPONSORED), ApplicationStatus.APPROVED))));

        Page<SponsorshipResponse> result =
                sponsorshipService.getMyApplications(ANA, null, ApplicationStatus.APPROVED, pageable);

        assertEquals(ApplicationStatus.APPROVED, result.getContent().get(0).status());
    }

    @Test
    void myApplicationsCanBeFilteredByDogAndStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        User ana = user(1L, ANA);
        when(userRepository.findByEmail(ANA)).thenReturn(Optional.of(ana));
        when(applicationRepository.findByUserIdAndDogIdAndStatus(1L, 5L, ApplicationStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<SponsorshipResponse> result =
                sponsorshipService.getMyApplications(ANA, 5L, ApplicationStatus.PENDING, pageable);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void applicationsWithoutStatusReturnsEverything() {
        Pageable pageable = PageRequest.of(0, 10);
        when(applicationRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(
                application(10L, user(1L, ANA), dog(5L, DogStatus.AVAILABLE), ApplicationStatus.APPROVED))));

        Page<SponsorshipResponse> result = sponsorshipService.getApplications(null, pageable);

        assertEquals(1, result.getTotalElements());
        verify(applicationRepository, never()).findByStatus(any(ApplicationStatus.class), any(Pageable.class));
    }

    @Test
    void applicationsWithStatusFiltersByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        when(applicationRepository.findByStatus(ApplicationStatus.PENDING, pageable)).thenReturn(new PageImpl<>(List.of(
                application(10L, user(1L, ANA), dog(5L, DogStatus.AVAILABLE), ApplicationStatus.PENDING))));

        Page<SponsorshipResponse> result = sponsorshipService.getApplications(ApplicationStatus.PENDING, pageable);

        assertEquals(ApplicationStatus.PENDING, result.getContent().get(0).status());
        verify(applicationRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void approveSponsorsTheDogAndRejectsOtherPendingApplications() {
        Dog rex = dog(5L, DogStatus.AVAILABLE);
        SponsorshipApplication winner = application(10L, user(1L, ANA), rex, ApplicationStatus.PENDING);
        SponsorshipApplication other = application(11L, user(2L, BOB), rex, ApplicationStatus.PENDING);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(winner));
        when(applicationRepository.findByDogIdAndStatus(5L, ApplicationStatus.PENDING))
                .thenReturn(List.of(winner, other));
        when(applicationRepository.save(any(SponsorshipApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SponsorshipResponse response = sponsorshipService.approve(10L, "Welcome");

        assertEquals(ApplicationStatus.APPROVED, response.status());
        assertEquals("Welcome", response.adminNote());
        assertEquals(DogStatus.SPONSORED, rex.getStatus());
        assertEquals(ApplicationStatus.REJECTED, other.getStatus());
        assertNotNull(other.getAdminNote());
        verify(dogRepository).save(rex);
    }

    @Test
    void approveRejectsApplicationThatIsNotPending() {
        Dog rex = dog(5L, DogStatus.AVAILABLE);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(
                application(10L, user(1L, ANA), rex, ApplicationStatus.REJECTED)));

        assertThrows(ConflictException.class, () -> sponsorshipService.approve(10L, null));
        verify(dogRepository, never()).save(any(Dog.class));
    }

    @Test
    void approveRejectsWhenDogIsNoLongerAvailable() {
        Dog rex = dog(5L, DogStatus.SPONSORED);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(
                application(10L, user(1L, ANA), rex, ApplicationStatus.PENDING)));

        assertThrows(ConflictException.class, () -> sponsorshipService.approve(10L, null));
        verify(dogRepository, never()).save(any(Dog.class));
    }

    @Test
    void approveThrowsWhenApplicationDoesNotExist() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sponsorshipService.approve(99L, null));
    }

    @Test
    void rejectKeepsTheDogAvailable() {
        Dog rex = dog(5L, DogStatus.AVAILABLE);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(
                application(10L, user(1L, ANA), rex, ApplicationStatus.PENDING)));
        when(applicationRepository.save(any(SponsorshipApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SponsorshipResponse response = sponsorshipService.reject(10L, "Not now");

        assertEquals(ApplicationStatus.REJECTED, response.status());
        assertEquals("Not now", response.adminNote());
        assertEquals(DogStatus.AVAILABLE, rex.getStatus());
        verify(dogRepository, never()).save(any(Dog.class));
    }

    @Test
    void rejectRefusesApplicationThatIsNotPending() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(
                application(10L, user(1L, ANA), dog(5L, DogStatus.SPONSORED), ApplicationStatus.APPROVED)));

        assertThrows(ConflictException.class, () -> sponsorshipService.reject(10L, null));
    }

    @Test
    void cancelChangesOwnPendingApplication() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(
                application(10L, user(1L, ANA), dog(5L, DogStatus.AVAILABLE), ApplicationStatus.PENDING)));
        when(applicationRepository.save(any(SponsorshipApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SponsorshipResponse response = sponsorshipService.cancel(ANA, 10L);

        assertEquals(ApplicationStatus.CANCELLED, response.status());
    }

    @Test
    void cancelHidesApplicationsOfOtherUsers() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(
                application(10L, user(2L, BOB), dog(5L, DogStatus.AVAILABLE), ApplicationStatus.PENDING)));

        assertThrows(ResourceNotFoundException.class, () -> sponsorshipService.cancel(ANA, 10L));
        verify(applicationRepository, never()).save(any(SponsorshipApplication.class));
    }

    @Test
    void cancelRefusesApplicationThatIsNotPending() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(
                application(10L, user(1L, ANA), dog(5L, DogStatus.SPONSORED), ApplicationStatus.APPROVED)));

        assertThrows(ConflictException.class, () -> sponsorshipService.cancel(ANA, 10L));
    }
}
