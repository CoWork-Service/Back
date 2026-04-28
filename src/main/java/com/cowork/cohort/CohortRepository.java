package com.cowork.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    List<Cohort> findByOrganizationIdOrderByYearDesc(Long organizationId);
}
