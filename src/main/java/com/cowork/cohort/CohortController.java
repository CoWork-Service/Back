package com.cowork.cohort;

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
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CohortController {

    private final CohortService cohortService;
    private final UserRepository userRepository;

    // ─── Cohort ───────────────────────────────────────────────────────────────

    @GetMapping("/cohorts")
    public ResponseEntity<ApiResponse<List<CohortResponse>>> getCohorts(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<CohortResponse> list = cohortService.getCohorts(user.getOrganization().getId())
                .stream().map(CohortResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/cohorts/{id}")
    public ResponseEntity<ApiResponse<CohortResponse>> getCohort(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohortService.getCohort(id))));
    }

    @PostMapping("/cohorts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CohortResponse>> createCohort(
            @RequestBody CohortCreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        Cohort cohort = cohortService.createCohort(user.getOrganization().getId(), req.getLabel(), req.getYear());
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohort)));
    }

    @PutMapping("/cohorts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CohortResponse>> updateCohort(
            @PathVariable Long id,
            @RequestBody CohortCreateRequest req) {
        Cohort cohort = cohortService.updateCohort(id, req.getLabel(), req.getYear());
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohort)));
    }

    // ─── Org Members ─────────────────────────────────────────────────────────

    @GetMapping("/org/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(@RequestParam Long cohortId) {
        List<MemberResponse> list = cohortService.getMembers(cohortId)
                .stream().map(MemberResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PutMapping("/org/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(
            @PathVariable Long id,
            @RequestBody MemberUpdateRequest req) {
        CohortMember member = cohortService.updateMember(id, req.getRole(), req.getDepartment());
        return ResponseEntity.ok(ApiResponse.ok(MemberResponse.of(member)));
    }

    @DeleteMapping("/org/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMember(@PathVariable Long id) {
        cohortService.deleteMember(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/org/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPending(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<Map<String, Object>> list = cohortService.getPendingUsers(user.getOrganization().getId())
                .stream().map(u -> Map.<String, Object>of(
                        "id", u.getId(), "name", u.getName(), "email", u.getEmail()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/org/pending/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approveUser(
            @PathVariable Long userId,
            @RequestParam Long cohortId) {
        cohortService.approveUser(userId, cohortId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/org/pending/{userId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectUser(@PathVariable Long userId) {
        cohortService.rejectUser(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/org/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateInvite(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        String code = cohortService.regenerateInviteCode(user.getOrganization().getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("inviteCode", code)));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    // ─── DTOs ────────────────────────────────────────────────────────────────

    @Getter
    static class CohortCreateRequest {
        private String label;
        private Integer year;
    }

    @Getter
    static class MemberUpdateRequest {
        private MemberRole role;
        private Department department;
    }

    record CohortResponse(Long id, String label, Integer year) {
        static CohortResponse of(Cohort c) {
            return new CohortResponse(c.getId(), c.getLabel(), c.getYear());
        }
    }

    record MemberResponse(Long id, Long userId, String name, String email, MemberRole role, Department department) {
        static MemberResponse of(CohortMember m) {
            return new MemberResponse(m.getId(), m.getUser().getId(), m.getUser().getName(),
                    m.getUser().getEmail(), m.getRole(), m.getDepartment());
        }
    }
}
