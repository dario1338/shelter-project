package org.unibl.etf.shelterapi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unibl.etf.shelterapi.model.Dog;
import org.unibl.etf.shelterapi.model.DogStatus;

public interface DogRepository extends JpaRepository<Dog, Long> {

    Page<Dog> findByStatus(DogStatus status, Pageable pageable);
}
