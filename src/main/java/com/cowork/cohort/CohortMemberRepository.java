package com.cowork.cohort;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface CohortMemberRepository extends JpaRepository<CohortMember, Long> {

    @EntityGraph(attributePaths = "user")
    List<CohortMember> findByCohortId(Long cohortId);

    @EntityGraph(attributePaths = "user")
    Optional<CohortMember> findByCohortIdAndUserId(Long cohortId, Long userId);

    boolean existsByCohortIdAndUserId(Long cohortId, Long userId);

    List<CohortMember> findByUserId(Long userId);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<CohortMember> findById(Long id);
}
