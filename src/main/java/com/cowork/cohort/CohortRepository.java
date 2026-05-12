package com.cowork.cohort;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    @EntityGraph(attributePaths = "organization")
    List<Cohort> findByOrganizationIdOrderByYearDesc(Long organizationId);

    @Override
    @EntityGraph(attributePaths = "organization")
    Optional<Cohort> findById(Long id);
}
