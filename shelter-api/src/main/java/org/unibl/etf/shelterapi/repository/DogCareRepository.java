package org.unibl.etf.shelterapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.unibl.etf.shelterapi.model.DogCare;

public interface DogCareRepository extends JpaRepository<DogCare, Long> {
}
