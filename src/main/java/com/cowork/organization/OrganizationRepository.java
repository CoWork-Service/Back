package com.cowork.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByInviteCode(String inviteCode);

    Optional<Organization> findFirstByDepartmentOrderByIdAsc(String department);
}
