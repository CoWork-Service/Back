package com.cowork.cohort;

import com.cowork.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    List<Cohort> findByOrganizationIdOrderByYearDesc(Long organizationId);
    Optional<Cohort> findByOrganizationAndLabel(Organization organization, String label);
}
