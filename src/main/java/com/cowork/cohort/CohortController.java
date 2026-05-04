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

/**
 * 코호트 및 조직 멤버 관리 컨트롤러 (CohortController)
 *
 * 역할:
 *   기수(Cohort) CRUD 와 조직 멤버 관리(승인·거절·역할 수정·초대코드 재발급) API 를 제공한다.
 *   기본 경로: /api
 *
 * 권한:
 *   - @PreAuthorize("hasRole('ADMIN')") 가 붙은 메서드는 ADMIN 역할을 가진 사용자만 접근 가능.
 *   - 그 외는 로그인 사용자 누구나 접근 가능.
 *
 * 주요 API 그룹:
 *   [코호트] GET/POST/PUT /api/cohorts
 *   [멤버]   GET/PUT/DELETE /api/org/members
 *   [승인]   GET/POST /api/org/pending
 *   [초대]   POST /api/org/invite
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CohortController {

    private final CohortService cohortService;
    private final UserRepository userRepository;

    // ─── 코호트 (Cohort) ──────────────────────────────────────────────────────

    /**
     * 코호트 목록 조회
     *
     * 동작: 현재 로그인 사용자의 소속 조직에 있는 모든 코호트를 반환한다.
     * 사용 시점: 대시보드·드롭다운에서 기수 목록을 표시할 때.
     * 인증 필요: JWT Access Token
     *
     * @param userDetails 현재 로그인 사용자
     * @return 코호트 목록 (id, label, year)
     */
    @GetMapping("/cohorts")
    public ResponseEntity<ApiResponse<List<CohortResponse>>> getCohorts(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<CohortResponse> list = cohortService.getCohorts(user.getOrganization().getId())
                .stream().map(CohortResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 코호트 단건 조회
     *
     * 동작: 코호트 ID 로 단일 코호트 정보를 반환한다.
     * 사용 시점: 코호트 상세 정보가 필요한 화면.
     * 인증 필요: JWT Access Token
     *
     * @param id 코호트 ID
     * @return 코호트 정보 (id, label, year)
     */
    @GetMapping("/cohorts/{id}")
    public ResponseEntity<ApiResponse<CohortResponse>> getCohort(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohortService.getCohort(id))));
    }

    /**
     * 코호트 생성 (ADMIN 전용)
     *
     * 동작: 현재 사용자의 조직에 새 코호트(기수)를 생성한다.
     * 사용 시점: 새 학기·기수가 시작될 때 관리자가 등록.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param req         { "label": "1기", "year": 2025 }
     * @param userDetails 현재 로그인 관리자
     * @return 생성된 코호트 정보
     */
    @PostMapping("/cohorts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CohortResponse>> createCohort(
            @RequestBody CohortCreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        Cohort cohort = cohortService.createCohort(user.getOrganization().getId(), req.getLabel(), req.getYear());
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohort)));
    }

    /**
     * 코호트 수정 (ADMIN 전용)
     *
     * 동작: 지정한 코호트의 label 과 year 를 수정한다.
     * 사용 시점: 기수 이름이나 연도가 잘못 입력되었을 때 정정.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param id  코호트 ID
     * @param req { "label": "2기", "year": 2026 }
     * @return 수정된 코호트 정보
     */
    @PutMapping("/cohorts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CohortResponse>> updateCohort(
            @PathVariable Long id,
            @RequestBody CohortCreateRequest req) {
        Cohort cohort = cohortService.updateCohort(id, req.getLabel(), req.getYear());
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohort)));
    }

    // ─── 조직 멤버 (Org Members) ──────────────────────────────────────────────

    /**
     * 코호트 멤버 목록 조회
     *
     * 동작: 특정 코호트에 속한 활성 멤버 목록을 반환한다.
     * 사용 시점: 멤버 관리 화면, 권한 확인 등.
     * 인증 필요: JWT Access Token
     *
     * @param cohortId 대상 코호트 ID
     * @return 멤버 목록 (id, userId, name, email, role, department)
     */
    @GetMapping("/org/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(@RequestParam Long cohortId) {
        List<MemberResponse> list = cohortService.getMembers(cohortId)
                .stream().map(MemberResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 멤버 역할/부서 수정 (ADMIN 전용)
     *
     * 동작: 지정한 CohortMember 의 역할과 부서를 변경한다.
     * 사용 시점: 관리자가 멤버 권한을 승격/강등하거나 부서를 재배치할 때.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param id  CohortMember ID
     * @param req { "role": "ADMIN", "department": "PLANNING" }
     * @return 수정된 멤버 정보
     */
    @PutMapping("/org/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(
            @PathVariable Long id,
            @RequestBody MemberUpdateRequest req) {
        CohortMember member = cohortService.updateMember(id, req.getRole(), req.getDepartment());
        return ResponseEntity.ok(ApiResponse.ok(MemberResponse.of(member)));
    }

    /**
     * 멤버 제거 (ADMIN 전용)
     *
     * 동작: 해당 CohortMember 레코드를 삭제한다. User 계정은 유지된다.
     * 사용 시점: 해당 기수에서 멤버를 내보낼 때.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param id CohortMember ID
     */
    @DeleteMapping("/org/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMember(@PathVariable Long id) {
        cohortService.deleteMember(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── 가입 승인 (Pending) ──────────────────────────────────────────────────

    /**
     * 가입 대기 사용자 목록 조회 (ADMIN 전용)
     *
     * 동작: 같은 조직에 가입 신청했지만 아직 승인되지 않은(joinStatus = PENDING) 사용자 목록 반환.
     * 사용 시점: 관리자가 새 가입 신청을 검토할 때.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param userDetails 현재 로그인 관리자
     * @return 대기 사용자 목록 [{ id, name, email }]
     */
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

    /**
     * 가입 신청 승인 (ADMIN 전용)
     *
     * 동작:
     *   1. 해당 User 의 joinStatus 를 ACTIVE 로 변경.
     *   2. 지정한 코호트에 CohortMember 레코드 생성 (기본 역할: EDITOR).
     *
     * 사용 시점: 관리자가 신청을 검토하고 특정 기수에 배정할 때.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param userId   승인할 사용자 ID
     * @param cohortId 배정할 코호트 ID
     */
    @PostMapping("/org/pending/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approveUser(
            @PathVariable Long userId,
            @RequestParam Long cohortId) {
        cohortService.approveUser(userId, cohortId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 가입 신청 거절 (ADMIN 전용)
     *
     * 동작: 해당 User 레코드를 삭제하여 가입 신청을 거절한다.
     * 사용 시점: 관리자가 부적합한 가입 신청을 거절할 때.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param userId 거절할 사용자 ID
     */
    @PostMapping("/org/pending/{userId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectUser(@PathVariable Long userId) {
        cohortService.rejectUser(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 초대 코드 재발급 (ADMIN 전용)
     *
     * 동작: 현재 조직의 초대 코드를 새 랜덤 코드로 교체한다.
     * 사용 시점: 코드 유출 또는 보안상 이유로 기존 코드를 무효화해야 할 때.
     * 인증 필요: JWT Access Token + ADMIN 역할
     *
     * @param userDetails 현재 로그인 관리자
     * @return { "inviteCode": "NEWCODE" }
     */
    @PostMapping("/org/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateInvite(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        String code = cohortService.regenerateInviteCode(user.getOrganization().getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("inviteCode", code)));
    }

    /** JWT 의 username(= userId) 로 User 엔티티를 로드하는 내부 헬퍼 */
    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** POST/PUT /api/cohorts 요청 바디 */
    @Getter
    static class CohortCreateRequest {
        private String label;
        private Integer year;
    }

    /** PUT /api/org/members/{id} 요청 바디 */
    @Getter
    static class MemberUpdateRequest {
        private MemberRole role;
        private Department department;
    }

    /** 코호트 응답 DTO */
    record CohortResponse(Long id, String label, Integer year) {
        static CohortResponse of(Cohort c) {
            return new CohortResponse(c.getId(), c.getLabel(), c.getYear());
        }
    }

    /** 멤버 응답 DTO */
    record MemberResponse(Long id, Long userId, String name, String email, MemberRole role, Department department) {
        static MemberResponse of(CohortMember m) {
            return new MemberResponse(m.getId(), m.getUser().getId(), m.getUser().getName(),
                    m.getUser().getEmail(), m.getRole(), m.getDepartment());
        }
    }
}
