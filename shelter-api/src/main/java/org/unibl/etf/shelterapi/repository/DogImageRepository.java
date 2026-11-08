package org.unibl.etf.shelterapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unibl.etf.shelterapi.model.DogImage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DogImageRepository extends JpaRepository<DogImage, Long> {

    /** The first photo of the list is the main one. */
    List<DogImage> findByDogIdOrderBySortOrderAscIdAsc(Long dogId);

    List<DogImage> findByDogIdInOrderBySortOrderAscIdAsc(Collection<Long> dogIds);

    Optional<DogImage> findByIdAndDogId(Long id, Long dogId);

    long countByDogId(Long dogId);

    @Query("select coalesce(max(i.sortOrder), 0) from DogImage i where i.dogId = :dogId")
    int findMaxSortOrder(@Param("dogId") Long dogId);
}
