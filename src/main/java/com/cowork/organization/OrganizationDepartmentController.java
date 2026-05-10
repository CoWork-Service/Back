package com.cowork.organization;

import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/org/departments")
@RequiredArgsConstructor
public class OrganizationDepartmentController {

    private final OrganizationDepartmentService organizationDepartmentService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganizationDepartmentResponse>>> getDepartments(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<OrganizationDepartmentResponse> departments = organizationDepartmentService
                .getDepartments(user.getOrganization().getId())
                .stream()
                .map(OrganizationDepartmentResponse::of)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(departments));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrganizationDepartmentResponse>>> replaceDepartments(
            @RequestBody OrganizationDepartmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<OrganizationDepartmentResponse> departments = organizationDepartmentService
                .replaceDepartments(user.getOrganization().getId(), request.getDepartments())
                .stream()
                .map(OrganizationDepartmentResponse::of)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(departments));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    @Getter
    static class OrganizationDepartmentRequest {
        private List<String> departments;
    }

    record OrganizationDepartmentResponse(Long id, String name, Integer sortOrder) {
        static OrganizationDepartmentResponse of(OrganizationDepartment department) {
            return new OrganizationDepartmentResponse(department.getId(), department.getName(), department.getSortOrder());
        }
    }
}
