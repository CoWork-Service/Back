package com.cowork.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationDepartmentRepository extends JpaRepository<OrganizationDepartment, Long> {

    List<OrganizationDepartment> findByOrganizationIdOrderBySortOrderAscNameAsc(Long organizationId);

    void deleteByOrganizationId(Long organizationId);
}
