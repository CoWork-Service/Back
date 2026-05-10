package com.cowork.organization;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationDepartmentService {

    private static final List<String> DEFAULT_DEPARTMENTS = List.of("회장단", "기획국", "총무부", "홍보국", "복지국");

    private final OrganizationRepository organizationRepository;
    private final OrganizationDepartmentRepository organizationDepartmentRepository;

    public List<OrganizationDepartment> getDepartments(Long organizationId) {
        return organizationDepartmentRepository.findByOrganizationIdOrderBySortOrderAscNameAsc(organizationId);
    }

    @Transactional
    public List<OrganizationDepartment> replaceDepartments(Long organizationId, List<String> departmentNames) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));

        organizationDepartmentRepository.deleteByOrganizationId(organizationId);
        List<String> normalized = normalize(departmentNames);

        for (int i = 0; i < normalized.size(); i++) {
            organizationDepartmentRepository.save(OrganizationDepartment.builder()
                    .organization(organization)
                    .name(normalized.get(i))
                    .sortOrder(i)
                    .build());
        }

        return getDepartments(organizationId);
    }

    private List<String> normalize(List<String> departmentNames) {
        List<String> source = departmentNames == null || departmentNames.isEmpty()
                ? DEFAULT_DEPARTMENTS
                : departmentNames;

        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String name : source) {
            if (name == null) continue;
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) deduplicated.add(trimmed);
        }

        if (deduplicated.isEmpty()) {
            deduplicated.addAll(DEFAULT_DEPARTMENTS);
        }
        return List.copyOf(deduplicated);
    }
}
