package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.SponsorshipApplication;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.DogRepository;
import org.unibl.etf.shelterapi.repository.SponsorshipApplicationRepository;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class SponsorshipRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SponsorshipApplicationRepository applicationRepository;

    @Autowired
    private DogRepository dogRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        applicationRepository.deleteAll();
        dogRepository.deleteAll();
    }

    private User saveUser() {
        return userRepository.save(new User(UUID.randomUUID() + "@example.com", "hashed", "Ana", "Anic", Role.USER));
    }

    private Dog saveDog() {
        Dog dog = new Dog();
        dog.setName("Rex");
        return dogRepository.save(dog);
    }

    @Test
    void sameUserCannotHaveTwoPendingApplicationsForTheSameDog() {
        User user = saveUser();
        Dog dog = saveDog();
        applicationRepository.saveAndFlush(new SponsorshipApplication(user, dog, "first"));
        SponsorshipApplication second = new SponsorshipApplication(user, dog, "second");

        assertThrows(DataIntegrityViolationException.class, () -> applicationRepository.saveAndFlush(second));
    }

    @Test
    void userCanApplyAgainAfterRejection() {
        User user = saveUser();
        Dog dog = saveDog();
        SponsorshipApplication first = new SponsorshipApplication(user, dog, "first");
        first.setStatus(ApplicationStatus.REJECTED);
        applicationRepository.saveAndFlush(first);

        applicationRepository.saveAndFlush(new SponsorshipApplication(user, dog, "second"));

        assertEquals(2, applicationRepository.count());
    }

    @Test
    void differentUsersCanHavePendingApplicationsForTheSameDog() {
        Dog dog = saveDog();
        applicationRepository.saveAndFlush(new SponsorshipApplication(saveUser(), dog, "first"));
        applicationRepository.saveAndFlush(new SponsorshipApplication(saveUser(), dog, "second"));

        assertEquals(2, applicationRepository.findByDogIdAndStatus(dog.getId(), ApplicationStatus.PENDING).size());
    }

    @Test
    void dogCanHaveOnlyOneApprovedApplication() {
        Dog dog = saveDog();
        SponsorshipApplication first = new SponsorshipApplication(saveUser(), dog, "first");
        first.setStatus(ApplicationStatus.APPROVED);
        applicationRepository.saveAndFlush(first);
        SponsorshipApplication second = new SponsorshipApplication(saveUser(), dog, "second");
        second.setStatus(ApplicationStatus.APPROVED);

        assertThrows(DataIntegrityViolationException.class, () -> applicationRepository.saveAndFlush(second));
    }

    @Test
    void deletingADogRemovesItsApplications() {
        Dog dog = saveDog();
        applicationRepository.saveAndFlush(new SponsorshipApplication(saveUser(), dog, "note"));

        dogRepository.deleteById(dog.getId());

        assertTrue(applicationRepository.findByDogIdAndStatus(dog.getId(), ApplicationStatus.PENDING).isEmpty());
    }
}
