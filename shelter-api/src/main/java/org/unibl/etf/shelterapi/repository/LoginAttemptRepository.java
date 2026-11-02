package org.unibl.etf.shelterapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.model.LoginAttempt;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    @Transactional
    @Modifying
    @Query("delete from LoginAttempt a where a.windowStart < :windowCutoff "
            + "and (a.lockedUntil is null or a.lockedUntil < :now)")
    int deleteStale(@Param("windowCutoff") Instant windowCutoff, @Param("now") Instant now);
}