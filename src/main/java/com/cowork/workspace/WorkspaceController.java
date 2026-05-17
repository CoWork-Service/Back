package com.cowork.workspace;

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
import java.util.stream.Collectors;

@Tag(name = "Workspace & Meeting", description = "워크스페이스 및 회의록 관리 API — 부서별 공간 관리, 회의록 CRUD, 첨부파일 연결")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;

    @Operation(
            summary = "워크스페이스 목록 조회",
            description = """
                    코호트의 워크스페이스 목록을 조회합니다.

                    **사용 시점:** 워크스페이스 목록 화면에서 공간 목록을 표시할 때.

                    각 항목에 **파일 수**와 **회의록 수**가 포함됩니다.

                    워크스페이스는 부서별 공간 단위입니다. `department`가 null이면 전체 공용 공간입니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "워크스페이스 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "department": "전체",
                                          "name": "공용 워크스페이스",
                                          "description": "전체 공용 자료 공간",
                                          "fileCount": 15,
                                          "meetingCount": 8,
                                          "updatedAt": "2025-05-09T14:00:00"
                                        },
                                        {
                                          "id": 2,
                                          "cohortId": 5,
                                          "department": "PLANNING",
                                          "name": "기획팀 워크스페이스",
                                          "description": "기획팀 전용 공간",
                                          "fileCount": 6,
                                          "meetingCount": 3,
                                          "updatedAt": "2025-05-08T10:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> getWorkspaces(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId) {
        List<WorkspaceResponse> workspaces = workspaceService.getWorkspaces(cohortId).stream()
                .map(WorkspaceResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(workspaces));
    }

    @Operation(
            summary = "워크스페이스 상세 조회",
            description = """
                    워크스페이스 기본 정보와 속한 회의록 목록을 반환합니다.

                    **사용 시점:** 워크스페이스 상세 화면 진입 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "워크스페이스 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "workspace": {
                                          "id": 2, "cohortId": 5, "department": "PLANNING",
                                          "name": "기획팀 워크스페이스", "description": "기획팀 전용 공간",
                                          "fileCount": 6, "meetingCount": 2, "updatedAt": "2025-05-08T10:00:00"
                                        },
                                        "meetings": [
                                          {
                                            "id": 1, "workspaceId": 2,
                                            "title": "MT 기획 회의", "date": "2025-02-15",
                                            "attendees": ["홍길동", "이철수"],
                                            "agenda": "MT 장소·일정 확정",
                                            "content": "가평 리버빌리지로 확정. 3월 14~15일.",
                                            "createdBy": 1, "eventId": 1,
                                            "createdAt": "2025-02-15T10:00:00",
                                            "updatedAt": "2025-02-15T10:00:00",
                                            "attachments": [
                                              { "id": 1, "fileItemId": 5, "storagePath": "files/mt_plan.pdf", "name": "MT_계획서.pdf", "size": 204800 }
                                            ]
                                          }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceDetailResponse>> getWorkspace(
            @Parameter(description = "워크스페이스 ID", required = true, example = "2") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(WorkspaceDetailResponse.of(workspaceService.getWorkspaceDetail(id))));
    }

    @Operation(
            summary = "워크스페이스 정보 수정",
            description = """
                    워크스페이스의 이름과 설명을 변경합니다.

                    **사용 시점:** 워크스페이스 설정 편집 화면에서 저장 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "워크스페이스 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "workspace": {
                                          "id": 2, "name": "기획팀 (수정)", "description": "기획팀 자료 공간"
                                        },
                                        "meetings": []
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceDetailResponse>> updateWorkspace(
            @Parameter(description = "워크스페이스 ID", required = true, example = "2") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "워크스페이스 수정 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "name": "기획팀 (수정)", "description": "기획팀 자료 공간" }
                            """)))
            @RequestBody WorkspaceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                WorkspaceDetailResponse.of(workspaceService.updateWorkspace(id, request.getName(), request.getDescription()))
        ));
    }

    @Operation(
            summary = "워크스페이스 내 회의록 목록 조회",
            description = """
                    특정 워크스페이스에 속한 회의록 목록을 첨부파일 정보와 함께 반환합니다.

                    **사용 시점:** 워크스페이스 내 회의록 탭 화면에서 목록 표시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의록 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1, "workspaceId": 2,
                                          "title": "MT 기획 회의", "date": "2025-02-15",
                                          "attendees": ["홍길동", "이철수"],
                                          "agenda": "MT 장소·일정 확정",
                                          "content": "가평 리버빌리지로 확정",
                                          "createdBy": 1, "eventId": 1,
                                          "createdAt": "2025-02-15T10:00:00",
                                          "updatedAt": "2025-02-15T10:00:00",
                                          "attachments": []
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/{id}/meetings")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getMeetings(
            @Parameter(description = "워크스페이스 ID", required = true, example = "2") @PathVariable Long id) {
        List<MeetingResponse> meetings = workspaceService.getMeetings(id).stream()
                .map(MeetingResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(meetings));
    }

    @Operation(
            summary = "회의록 생성",
            description = """
                    워크스페이스에 새 회의록을 생성합니다. 첨부파일 목록도 함께 저장됩니다.

                    **사용 시점:** 회의 후 기록을 작성할 때.

                    작성자(`createdBy`)는 JWT 토큰에서 자동으로 설정됩니다.

                    **첨부파일:** 파일 API로 업로드한 `FileItem`의 ID를 `fileItemId`에 연결합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의록 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 5, "workspaceId": 2,
                                        "title": "종강 파티 기획 회의", "date": "2025-05-10",
                                        "attendees": ["홍길동", "이철수", "박지훈"],
                                        "agenda": "종강 파티 장소·메뉴·예산 확정",
                                        "content": "장소: 동아리방, 예산: 30만원, 메뉴: 중식",
                                        "createdBy": 1, "eventId": 2,
                                        "createdAt": "2025-05-10T15:00:00",
                                        "updatedAt": "2025-05-10T15:00:00",
                                        "attachments": [
                                          { "id": 3, "fileItemId": 20, "storagePath": "files/party_budget.xlsx", "name": "파티예산.xlsx", "size": 12800 }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping("/{id}/meetings")
    public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(
            @Parameter(description = "워크스페이스 ID", required = true, example = "2") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "회의록 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "title": "종강 파티 기획 회의",
                              "date": "2025-05-10",
                              "attendees": ["홍길동", "이철수", "박지훈"],
                              "agenda": "종강 파티 장소·메뉴·예산 확정",
                              "content": "장소: 동아리방, 예산: 30만원, 메뉴: 중식",
                              "eventId": 2,
                              "attachments": [
                                {
                                  "fileItemId": 20,
                                  "storagePath": "files/party_budget.xlsx",
                                  "name": "파티예산.xlsx",
                                  "size": 12800
                                }
                              ]
                            }
                            """)))
            @RequestBody MeetingRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        WorkspaceService.MeetingDetail detail = workspaceService.createMeeting(
                id, request.getTitle(), request.getDate(), request.getAttendees(),
                request.getAgenda(), request.getContent(), user.getId(),
                request.getEventId(), toAttachmentPayloads(request.getAttachments())
        );
        return ResponseEntity.ok(ApiResponse.ok(MeetingResponse.of(detail.meeting())));
    }

    @Operation(
            summary = "회의록 단건 조회",
            description = """
                    특정 워크스페이스의 특정 회의록 상세 정보를 반환합니다.

                    **사용 시점:** 회의록 상세 화면 진입 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회의록을 찾을 수 없음")
    })
    @GetMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(
            @Parameter(description = "워크스페이스 ID", required = true, example = "2") @PathVariable Long id,
            @Parameter(description = "회의록 ID", required = true, example = "5") @PathVariable Long meetingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                MeetingResponse.of(workspaceService.getMeetingDetail(id, meetingId).meeting())
        ));
    }

    @Operation(
            summary = "회의록 수정",
            description = """
                    회의록 내용을 수정하고 첨부파일 목록을 교체합니다.

                    **사용 시점:** 회의록 편집 화면에서 저장 시.

                    첨부파일은 기존 목록 삭제 후 재삽입 방식으로 교체됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의록 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회의록을 찾을 수 없음")
    })
    @PutMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<MeetingResponse>> updateMeeting(
            @Parameter(description = "워크스페이스 ID", required = true, example = "2") @PathVariable Long id,
            @Parameter(description = "회의록 ID", required = true, example = "5") @PathVariable Long meetingId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "회의록 수정 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "title": "종강 파티 기획 회의 (수정)",
                              "date": "2025-05-10",
                              "attendees": ["홍길동", "이철수", "박지훈", "최수아"],
                              "agenda": "종강 파티 최종 확정",
                              "content": "장소: 동아리방 + 회의실, 예산: 35만원, 메뉴: 중식",
                              "eventId": 2,
                              "attachments": []
                            }
                            """)))
            @RequestBody MeetingRequest request) {
        WorkspaceService.MeetingDetail detail = workspaceService.updateMeeting(
                id, meetingId, request.getTitle(), request.getDate(), request.getAttendees(),
                request.getAgenda(), request.getContent(), request.getEventId(),
                toAttachmentPayloads(request.getAttachments())
        );
        return ResponseEntity.ok(ApiResponse.ok(MeetingResponse.of(detail.meeting())));
    }

    @Operation(
            summary = "회의록 삭제",
            description = """
                    회의록과 연관된 첨부파일 레코드를 삭제합니다.

                    **사용 시점:** 잘못 작성된 회의록을 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의록 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회의록을 찾을 수 없음")
    })
    @DeleteMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<Void>> deleteMeeting(
            @Parameter(description = "워크스페이스 ID", required = true, example = "2") @PathVariable Long id,
            @Parameter(description = "회의록 ID", required = true, example = "5") @PathVariable Long meetingId) {
        workspaceService.deleteMeeting(id, meetingId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    private List<WorkspaceService.AttachmentPayload> toAttachmentPayloads(List<AttachmentRequest> attachments) {
        if (attachments == null) return List.of();
        return attachments.stream()
                .map(attachment -> new WorkspaceService.AttachmentPayload(
                        attachment.getFileItemId(), attachment.getStoragePath(),
                        attachment.getName(), attachment.getSize()))
                .collect(Collectors.toList());
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    @Getter
    static class WorkspaceRequest {
        private String name;
        private String description;
    }

    @Getter
    static class MeetingRequest {
        private String title;
        private LocalDate date;
        private List<String> attendees;
        private String agenda;
        private String content;
        private Long eventId;
        private List<AttachmentRequest> attachments;
    }

    @Getter
    static class AttachmentRequest {
        private Long fileItemId;
        private String storagePath;
        private String name;
        private Long size;
    }

    record WorkspaceResponse(Long id, Long cohortId, String department, String name, String description,
                             long fileCount, long meetingCount, LocalDateTime updatedAt) {
        static WorkspaceResponse of(WorkspaceService.WorkspaceSummary summary) {
            Workspace workspace = summary.workspace();
            return new WorkspaceResponse(
                    workspace.getId(), workspace.getCohortId(),
                    workspace.getDepartment() != null ? workspace.getDepartment() : "전체",
                    workspace.getName(), workspace.getDescription(),
                    summary.fileCount(), summary.meetingCount(), workspace.getUpdatedAt()
            );
        }
    }

    record WorkspaceDetailResponse(WorkspaceResponse workspace, List<MeetingResponse> meetings) {
        static WorkspaceDetailResponse of(WorkspaceService.WorkspaceDetail detail) {
            Workspace workspace = detail.workspace();
            return new WorkspaceDetailResponse(
                    new WorkspaceResponse(
                            workspace.getId(), workspace.getCohortId(),
                            workspace.getDepartment() != null ? workspace.getDepartment() : "전체",
                            workspace.getName(), workspace.getDescription(),
                            detail.fileCount(), detail.meetings().size(), workspace.getUpdatedAt()
                    ),
                    detail.meetings().stream().map(MeetingResponse::of).collect(Collectors.toList())
            );
        }
    }

    record MeetingResponse(Long id, Long workspaceId, String title, LocalDate date, List<String> attendees,
                           String agenda, String content, Long createdBy, Long eventId,
                           LocalDateTime createdAt, LocalDateTime updatedAt, List<MeetingAttachmentResponse> attachments) {
        static MeetingResponse of(WorkspaceService.MeetingSummary summary) {
            Meeting meeting = summary.meeting();
            return new MeetingResponse(
                    meeting.getId(), meeting.getWorkspaceId(), meeting.getTitle(), meeting.getDate(),
                    meeting.getAttendees(), meeting.getAgenda(), meeting.getContent(),
                    meeting.getCreatedBy(), meeting.getEventId(),
                    meeting.getCreatedAt(), meeting.getUpdatedAt(),
                    summary.attachments().stream().map(MeetingAttachmentResponse::of).collect(Collectors.toList())
            );
        }
    }

    record MeetingAttachmentResponse(Long id, Long fileItemId, String storagePath, String name, Long size) {
        static MeetingAttachmentResponse of(MeetingAttachment attachment) {
            return new MeetingAttachmentResponse(
                    attachment.getId(), attachment.getFileItemId(),
                    attachment.getStoragePath(), attachment.getName(), attachment.getSize()
            );
        }
    }
}
