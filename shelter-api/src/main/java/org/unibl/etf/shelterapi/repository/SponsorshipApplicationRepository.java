package org.unibl.etf.shelterapi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unibl.etf.shelterapi.model.ApplicationStatus;
import org.unibl.etf.shelterapi.model.SponsorshipApplication;

import java.util.List;

public interface SponsorshipApplicationRepository extends JpaRepository<SponsorshipApplication, Long> {

    boolean existsByUserIdAndDogIdAndStatus(Long userId, Long dogId, ApplicationStatus status);

    Page<SponsorshipApplication> findByUserId(Long userId, Pageable pageable);

    Page<SponsorshipApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    List<SponsorshipApplication> findByDogIdAndStatus(Long dogId, ApplicationStatus status);
}
