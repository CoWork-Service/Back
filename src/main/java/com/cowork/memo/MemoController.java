package com.cowork.memo;

import com.cowork.cohort.Department;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Memo", description = "메모/업무 관리 API — 할 일·메모 CRUD, 상태/우선순위 변경")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/memos")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;
    private final UserRepository userRepository;

    @Operation(
            summary = "메모 목록 조회",
            description = """
                    코호트의 메모/할 일 목록을 조회합니다.

                    **사용 시점:** 업무 목록 화면에서 메모 표시 및 필터 적용 시.

                    **상태(status) 값:** `OPEN`(진행중) / `CLOSED`(완료)

                    **우선순위(priority) 값:** `LOW` / `NORMAL` / `HIGH`

                    **부서(department) 값:** PLANNING / MARKETING / OPERATION / FINANCE / GENERAL
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메모 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "title": "MT 장소 예약",
                                          "content": "가평 리버빌리지 3월 14~15일 예약 필요",
                                          "department": "PLANNING",
                                          "priority": "HIGH",
                                          "status": "OPEN",
                                          "dueDate": "2025-02-28",
                                          "author": "홍길동",
                                          "createdAt": "2025-02-10T09:00:00",
                                          "updatedAt": "2025-02-10T09:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<MemoResponse>>> getMemos(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "상태 필터 (OPEN / CLOSED)", example = "OPEN") @RequestParam(required = false) MemoStatus status,
            @Parameter(description = "우선순위 필터 (LOW / NORMAL / HIGH)", example = "HIGH") @RequestParam(required = false) MemoPriority priority,
            @Parameter(description = "부서 필터") @RequestParam(required = false) Department department) {
        List<MemoResponse> list = memoService.getMemos(cohortId, status, priority, department)
                .stream().map(MemoResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(
            summary = "메모 생성",
            description = """
                    새 메모/할 일을 생성합니다. 작성자는 JWT 토큰에서 자동으로 설정됩니다.

                    **사용 시점:** 새로운 할 일이나 메모를 추가할 때.

                    **초기 상태:** `status`를 지정하지 않으면 기본값은 `OPEN`입니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메모 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 5,
                                        "cohortId": 5,
                                        "title": "포스터 제작",
                                        "content": "행사 포스터 A3 사이즈, 100장",
                                        "department": "MARKETING",
                                        "priority": "NORMAL",
                                        "status": "OPEN",
                                        "dueDate": "2025-06-01",
                                        "author": "이철수",
                                        "createdAt": "2025-05-10T10:00:00",
                                        "updatedAt": "2025-05-10T10:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<MemoResponse>> createMemo(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "메모 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "cohortId": 5,
                              "title": "포스터 제작",
                              "content": "행사 포스터 A3 사이즈, 100장",
                              "department": "MARKETING",
                              "priority": "NORMAL",
                              "status": "OPEN",
                              "dueDate": "2025-06-01"
                            }
                            """)))
            @RequestBody MemoRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        Memo memo = memoService.createMemo(req.getCohortId(), req.getTitle(), req.getContent(),
                req.getDepartment(), req.getPriority(), req.getStatus(), req.getDueDate(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @Operation(
            summary = "메모 수정",
            description = """
                    메모 내용을 수정합니다.

                    **사용 시점:** 메모 편집 폼에서 저장 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메모 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 5,
                                        "cohortId": 5,
                                        "title": "포스터 제작 (수정)",
                                        "content": "행사 포스터 A3 사이즈, 150장으로 변경",
                                        "department": "MARKETING",
                                        "priority": "HIGH",
                                        "status": "OPEN",
                                        "dueDate": "2025-06-01",
                                        "author": "이철수",
                                        "createdAt": "2025-05-10T10:00:00",
                                        "updatedAt": "2025-05-10T15:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메모를 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoResponse>> updateMemo(
            @Parameter(description = "메모 ID", required = true, example = "5") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "메모 수정 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "title": "포스터 제작 (수정)",
                              "content": "행사 포스터 A3 사이즈, 150장으로 변경",
                              "department": "MARKETING",
                              "priority": "HIGH",
                              "status": "OPEN",
                              "dueDate": "2025-06-01"
                            }
                            """)))
            @RequestBody MemoRequest req) {
        Memo memo = memoService.updateMemo(id, req.getTitle(), req.getContent(),
                req.getDepartment(), req.getPriority(), req.getStatus(), req.getDueDate());
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @Operation(
            summary = "메모 상태 변경",
            description = """
                    메모의 상태(완료 여부)를 변경합니다.

                    **사용 시점:** 할 일 체크박스 클릭 시 또는 상태 드롭다운 변경 시.

                    **status 값:** `OPEN` (진행중) / `CLOSED` (완료)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 5,
                                        "status": "CLOSED",
                                        "updatedAt": "2025-05-10T17:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메모를 찾을 수 없음")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<MemoResponse>> updateStatus(
            @Parameter(description = "메모 ID", required = true, example = "5") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "상태 변경 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "status": "CLOSED" }
                            """)))
            @RequestBody Map<String, String> body) {
        Memo memo = memoService.updateStatus(id, MemoStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @Operation(
            summary = "메모 우선순위 변경",
            description = """
                    메모의 우선순위를 변경합니다.

                    **사용 시점:** 우선순위 드롭다운 변경 시.

                    **priority 값:** `LOW` / `NORMAL` / `HIGH`
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "우선순위 변경 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": { "id": 5, "priority": "HIGH", "updatedAt": "2025-05-10T17:05:00" },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메모를 찾을 수 없음")
    })
    @PatchMapping("/{id}/priority")
    public ResponseEntity<ApiResponse<MemoResponse>> updatePriority(
            @Parameter(description = "메모 ID", required = true, example = "5") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "우선순위 변경 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "priority": "HIGH" }
                            """)))
            @RequestBody Map<String, String> body) {
        Memo memo = memoService.updatePriority(id, MemoPriority.valueOf(body.get("priority")));
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @Operation(
            summary = "메모 삭제",
            description = """
                    메모를 삭제합니다.

                    **사용 시점:** 더 이상 필요없는 메모를 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메모 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메모를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMemo(
            @Parameter(description = "메모 ID", required = true, example = "5") @PathVariable Long id) {
        memoService.deleteMemo(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Getter
    static class MemoRequest {
        private Long cohortId;
        private String title;
        private String content;
        private Department department;
        private MemoPriority priority;
        private MemoStatus status;
        private LocalDate dueDate;
    }

    record MemoResponse(Long id, Long cohortId, String title, String content, String department,
                        String priority, String status, LocalDate dueDate, String author,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        static MemoResponse of(Memo m) {
            return new MemoResponse(m.getId(), m.getCohortId(), m.getTitle(), m.getContent(),
                    m.getDepartment() != null ? m.getDepartment().name() : null,
                    m.getPriority().name(), m.getStatus().name(), m.getDueDate(), m.getAuthor(),
                    m.getCreatedAt(), m.getUpdatedAt());
        }
    }
}
