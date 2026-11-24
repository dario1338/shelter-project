package org.unibl.etf.shelterapi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unibl.etf.shelterapi.model.DogVisit;
import org.unibl.etf.shelterapi.model.VisitStatus;

import java.time.Instant;

public interface DogVisitRepository extends JpaRepository<DogVisit, Long> {

    boolean existsByDogIdAndVisitAtAndStatus(Long dogId, Instant visitAt, VisitStatus status);

    Page<DogVisit> findByUserEmail(String email, Pageable pageable);
}
