package com.cowork.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortMemberRepository extends JpaRepository<CohortMember, Long> {

    List<CohortMember> findByCohortId(Long cohortId);

    Optional<CohortMember> findByCohortIdAndUserId(Long cohortId, Long userId);

    boolean existsByCohortIdAndUserId(Long cohortId, Long userId);

    List<CohortMember> findByUserId(Long userId);
}
