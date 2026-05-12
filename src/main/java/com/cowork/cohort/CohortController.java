package com.cowork.cohort;

import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Cohort & Organization", description = "기수(Cohort) 관리 및 조직 멤버 관리 API — 코호트 CRUD, 멤버 승인/거절/역할변경, 초대코드 재발급")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CohortController {

    private final CohortService cohortService;
    private final UserRepository userRepository;

    // ─── 코호트 (Cohort) ──────────────────────────────────────────────────────

    @Operation(
            summary = "코호트 목록 조회",
            description = """
                    현재 로그인 사용자의 소속 조직에 있는 모든 기수(Cohort) 목록을 반환합니다.

                    **사용 시점:** 대시보드·드롭다운에서 기수 목록을 표시할 때.

                    기수는 학기/연도 단위의 그룹으로, 학생 명단·자산·설문 등 대부분의 데이터가 cohortId 기준으로 구분됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코호트 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        { "id": 1, "label": "1기", "year": 2023, "organizationName": "멋쟁이사자처럼" },
                                        { "id": 2, "label": "2기", "year": 2024, "organizationName": "멋쟁이사자처럼" },
                                        { "id": 3, "label": "3기", "year": 2025, "organizationName": "멋쟁이사자처럼" }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/cohorts")
    public ResponseEntity<ApiResponse<List<CohortResponse>>> getCohorts(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<CohortResponse> list = cohortService.getCohorts(user.getOrganization().getId())
                .stream().map(CohortResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(
            summary = "코호트 단건 조회",
            description = """
                    코호트 ID로 단일 기수 정보를 조회합니다.

                    **사용 시점:** 코호트 상세 정보가 필요한 화면.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코호트 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": { "id": 3, "label": "3기", "year": 2025, "organizationName": "멋쟁이사자처럼" },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코호트를 찾을 수 없음")
    })
    @GetMapping("/cohorts/{id}")
    public ResponseEntity<ApiResponse<CohortResponse>> getCohort(
            @Parameter(description = "코호트 ID", required = true, example = "3") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohortService.getCohort(id))));
    }

    @Operation(
            summary = "코호트 생성 [ADMIN 전용]",
            description = """
                    현재 사용자의 조직에 새 기수(Cohort)를 생성합니다.

                    **사용 시점:** 새 학기·기수가 시작될 때 관리자가 등록.

                    **권한:** ADMIN 역할 필요
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코호트 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": { "id": 4, "label": "4기", "year": 2026, "organizationName": "멋쟁이사자처럼" },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @PostMapping("/cohorts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CohortResponse>> createCohort(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "코호트 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "label": "4기", "year": 2026 }
                            """)))
            @RequestBody CohortCreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        Cohort cohort = cohortService.createCohort(user.getOrganization().getId(), req.getLabel(), req.getYear());
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohort)));
    }

    @Operation(
            summary = "코호트 수정 [ADMIN 전용]",
            description = """
                    기수의 이름(label)과 연도(year)를 수정합니다.

                    **사용 시점:** 기수 이름이나 연도가 잘못 입력되었을 때 정정.

                    **권한:** ADMIN 역할 필요
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코호트 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": { "id": 3, "label": "3기 (수정)", "year": 2025, "organizationName": "멋쟁이사자처럼" },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코호트를 찾을 수 없음")
    })
    @PutMapping("/cohorts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CohortResponse>> updateCohort(
            @Parameter(description = "코호트 ID", required = true, example = "3") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "코호트 수정 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "label": "3기 (수정)", "year": 2025 }
                            """)))
            @RequestBody CohortCreateRequest req) {
        Cohort cohort = cohortService.updateCohort(id, req.getLabel(), req.getYear());
        return ResponseEntity.ok(ApiResponse.ok(CohortResponse.of(cohort)));
    }

    // ─── 조직 멤버 (Org Members) ──────────────────────────────────────────────

    @Operation(
            summary = "코호트 멤버 목록 조회",
            description = """
                    특정 코호트에 속한 활성 멤버 목록을 조회합니다.

                    **사용 시점:** 멤버 관리 화면, 권한 확인 등.

                    **역할(role) 값:** ADMIN / EDITOR / VIEWER

                    **부서(department) 값:** PLANNING / MARKETING / OPERATION / FINANCE / GENERAL
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "멤버 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "userId": 10,
                                          "name": "홍길동",
                                          "email": "hong@example.com",
                                          "role": "ADMIN",
                                          "department": "PLANNING"
                                        },
                                        {
                                          "id": 2,
                                          "userId": 11,
                                          "name": "이철수",
                                          "email": "lee@example.com",
                                          "role": "EDITOR",
                                          "department": "MARKETING"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/org/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "3") @RequestParam Long cohortId) {
        List<MemberResponse> list = cohortService.getMembers(cohortId)
                .stream().map(MemberResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(
            summary = "멤버 역할/부서 수정 [ADMIN 전용]",
            description = """
                    지정한 멤버의 역할(role)과 부서(department)를 변경합니다.

                    **사용 시점:** 관리자가 멤버 권한을 승격/강등하거나 부서를 재배치할 때.

                    **역할(role) 값:** ADMIN / EDITOR / VIEWER

                    **부서(department) 값:** PLANNING / MARKETING / OPERATION / FINANCE / GENERAL

                    **권한:** ADMIN 역할 필요
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "멤버 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 2,
                                        "userId": 11,
                                        "name": "이철수",
                                        "email": "lee@example.com",
                                        "role": "ADMIN",
                                        "department": "PLANNING"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @PutMapping("/org/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(
            @Parameter(description = "CohortMember ID", required = true, example = "2") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "역할/부서 수정 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "role": "ADMIN", "department": "PLANNING" }
                            """)))
            @RequestBody MemberUpdateRequest req) {
        CohortMember member = cohortService.updateMember(id, req.getRole(), req.getDepartment());
        return ResponseEntity.ok(ApiResponse.ok(MemberResponse.of(member)));
    }

    @Operation(
            summary = "멤버 제거 [ADMIN 전용]",
            description = """
                    해당 기수에서 멤버를 제거합니다. 사용자 계정(User)은 유지됩니다.

                    **사용 시점:** 해당 기수에서 멤버를 내보낼 때.

                    **권한:** ADMIN 역할 필요
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "멤버 제거 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @DeleteMapping("/org/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMember(
            @Parameter(description = "CohortMember ID", required = true, example = "2") @PathVariable Long id) {
        cohortService.deleteMember(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── 가입 승인 (Pending) ──────────────────────────────────────────────────

    @Operation(
            summary = "가입 대기 사용자 목록 조회 [ADMIN 전용]",
            description = """
                    조직에 가입 신청했지만 아직 승인되지 않은(joinStatus = PENDING) 사용자 목록을 반환합니다.

                    **사용 시점:** 관리자가 새 가입 신청을 검토할 때.

                    **권한:** ADMIN 역할 필요
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대기 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        { "id": 20, "name": "박지훈", "email": "park@example.com" },
                                        { "id": 21, "name": "최수아", "email": "choi@example.com" }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
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

    @Operation(
            summary = "가입 신청 승인 [ADMIN 전용]",
            description = """
                    대기 중인 사용자의 가입을 승인하고 특정 기수에 배정합니다.

                    **사용 시점:** 관리자가 신청을 검토하고 특정 기수에 배정할 때.

                    **처리 순서:**
                    1. 사용자의 `joinStatus`를 ACTIVE로 변경
                    2. 지정한 코호트에 CohortMember 레코드 생성 (기본 역할: EDITOR)

                    **권한:** ADMIN 역할 필요
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가입 승인 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @PostMapping("/org/pending/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approveUser(
            @Parameter(description = "승인할 사용자 ID", required = true, example = "20") @PathVariable Long userId,
            @Parameter(description = "배정할 코호트 ID", required = true, example = "3") @RequestParam Long cohortId) {
        cohortService.approveUser(userId, cohortId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "가입 신청 거절 [ADMIN 전용]",
            description = """
                    대기 중인 사용자의 가입 신청을 거절합니다. 해당 사용자 레코드가 삭제됩니다.

                    **사용 시점:** 관리자가 부적합한 가입 신청을 거절할 때.

                    **권한:** ADMIN 역할 필요
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가입 거절 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @PostMapping("/org/pending/{userId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectUser(
            @Parameter(description = "거절할 사용자 ID", required = true, example = "21") @PathVariable Long userId) {
        cohortService.rejectUser(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "초대 코드 재발급 [ADMIN 전용]",
            description = """
                    조직의 초대 코드를 새 랜덤 코드로 교체합니다.

                    **사용 시점:** 코드 유출 또는 보안상 이유로 기존 코드를 무효화해야 할 때.

                    **권한:** ADMIN 역할 필요

                    재발급된 초대 코드는 회원가입 시 `inviteCode` 필드에 사용합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초대 코드 재발급 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": { "inviteCode": "XK9M2P7Q4R8T1AZ6" },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
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

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

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

    record CohortResponse(Long id, String label, Integer year, String organizationName) {
        static CohortResponse of(Cohort c) {
            return new CohortResponse(c.getId(), c.getLabel(), c.getYear(), c.getOrganization().getName());
        }
    }

    record MemberResponse(Long id, Long userId, String name, String email, String studentId,
                          MemberRole role, Department department, java.time.LocalDateTime joinedAt) {
        static MemberResponse of(CohortMember m) {
            return new MemberResponse(m.getId(), m.getUser().getId(), m.getUser().getName(),
                    m.getUser().getEmail(), m.getUser().getStudentId(), m.getRole(),
                    m.getDepartment(), m.getCreatedAt());
        }
    }
}
