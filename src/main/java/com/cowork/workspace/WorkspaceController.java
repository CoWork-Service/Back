package com.cowork.workspace;

import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
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

/**
 * 워크스페이스 및 회의록 컨트롤러 (WorkspaceController)
 *
 * 역할:
 *   코호트 내 워크스페이스(부서별 공간)와 그 안에 속한 회의록을 관리하는 API 를 제공한다.
 *   기본 경로: /api/workspaces
 *
 * 구조:
 *   - 워크스페이스 : 부서 또는 전체 공용 공간 단위 (이름·설명 수정만 가능, 생성·삭제는 별도 관리)
 *   - 회의록      : 워크스페이스 내에 속하는 회의 기록 (첨부파일 포함)
 *
 * 인증 필요: 모든 엔드포인트에 JWT Access Token 필요
 */
@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;

    /**
     * 워크스페이스 목록 조회
     *
     * 동작: 특정 코호트에 속한 워크스페이스 목록을 반환한다.
     *       각 항목에 파일 수와 회의록 수가 포함된다.
     * 사용 시점: 워크스페이스 목록 화면에서 공간 목록을 표시할 때.
     *
     * @param cohortId 조회할 코호트 ID
     * @return 워크스페이스 목록 (파일 수·회의록 수 포함)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> getWorkspaces(@RequestParam Long cohortId) {
        List<WorkspaceResponse> workspaces = workspaceService.getWorkspaces(cohortId).stream()
                .map(WorkspaceResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(workspaces));
    }

    /**
     * 워크스페이스 상세 조회
     *
     * 동작: 워크스페이스 기본 정보 + 속한 회의록 목록을 반환한다.
     * 사용 시점: 워크스페이스 상세 화면 진입 시.
     *
     * @param id 워크스페이스 ID
     * @return 워크스페이스 정보 + 회의록 목록
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceDetailResponse>> getWorkspace(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(WorkspaceDetailResponse.of(workspaceService.getWorkspaceDetail(id))));
    }

    /**
     * 워크스페이스 정보 수정
     *
     * 동작: 워크스페이스의 이름과 설명을 변경한다.
     * 사용 시점: 워크스페이스 설정 편집 화면에서 저장 시.
     *
     * @param id      워크스페이스 ID
     * @param request { "name": "새이름", "description": "새설명" }
     * @return 수정된 워크스페이스 상세 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceDetailResponse>> updateWorkspace(
            @PathVariable Long id,
            @RequestBody WorkspaceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                WorkspaceDetailResponse.of(workspaceService.updateWorkspace(id, request.getName(), request.getDescription()))
        ));
    }

    /**
     * 워크스페이스 내 회의록 목록 조회
     *
     * 동작: 특정 워크스페이스에 속한 회의록 목록을 첨부파일 정보와 함께 반환한다.
     * 사용 시점: 워크스페이스 내 회의록 탭 화면에서 목록 표시.
     *
     * @param id 워크스페이스 ID
     * @return 회의록 목록 (첨부파일 포함)
     */
    @GetMapping("/{id}/meetings")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getMeetings(@PathVariable Long id) {
        List<MeetingResponse> meetings = workspaceService.getMeetings(id).stream()
                .map(MeetingResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(meetings));
    }

    /**
     * 회의록 생성
     *
     * 동작: 워크스페이스에 새 회의록을 생성한다.
     *       첨부파일(attachments) 목록도 함께 저장된다.
     *       작성자(createdBy)는 JWT 토큰에서 자동으로 설정된다.
     * 사용 시점: 회의 후 기록을 작성할 때.
     *
     * @param id          워크스페이스 ID
     * @param request     회의록 정보 (title, date, attendees, agenda, content, eventId, attachments)
     * @param userDetails 현재 로그인 사용자 (작성자)
     * @return 생성된 회의록 정보
     */
    @PostMapping("/{id}/meetings")
    public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(
            @PathVariable Long id,
            @RequestBody MeetingRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        WorkspaceService.MeetingDetail detail = workspaceService.createMeeting(
                id,
                request.getTitle(),
                request.getDate(),
                request.getAttendees(),
                request.getAgenda(),
                request.getContent(),
                user.getId(),
                request.getEventId(),
                toAttachmentPayloads(request.getAttachments())
        );
        return ResponseEntity.ok(ApiResponse.ok(MeetingResponse.of(detail.meeting())));
    }

    /**
     * 회의록 단건 조회
     *
     * 동작: 특정 워크스페이스의 특정 회의록 상세 정보를 반환한다.
     *       워크스페이스 ID 와 회의록 ID 를 모두 검증하여 소속 확인.
     * 사용 시점: 회의록 상세 화면 진입 시.
     *
     * @param id        워크스페이스 ID
     * @param meetingId 회의록 ID
     * @return 회의록 상세 정보 (첨부파일 포함)
     */
    @GetMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(
            @PathVariable Long id,
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                MeetingResponse.of(workspaceService.getMeetingDetail(id, meetingId).meeting())
        ));
    }

    /**
     * 회의록 수정
     *
     * 동작: 회의록 내용을 수정하고 첨부파일 목록을 교체(기존 삭제 후 재삽입)한다.
     * 사용 시점: 회의록 편집 화면에서 저장 시.
     *
     * @param id        워크스페이스 ID
     * @param meetingId 회의록 ID
     * @param request   수정할 회의록 정보
     * @return 수정된 회의록 정보
     */
    @PutMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<MeetingResponse>> updateMeeting(
            @PathVariable Long id,
            @PathVariable Long meetingId,
            @RequestBody MeetingRequest request) {
        WorkspaceService.MeetingDetail detail = workspaceService.updateMeeting(
                id,
                meetingId,
                request.getTitle(),
                request.getDate(),
                request.getAttendees(),
                request.getAgenda(),
                request.getContent(),
                request.getEventId(),
                toAttachmentPayloads(request.getAttachments())
        );
        return ResponseEntity.ok(ApiResponse.ok(MeetingResponse.of(detail.meeting())));
    }

    /**
     * 회의록 삭제
     *
     * 동작: 회의록과 연관된 첨부파일 레코드를 삭제한다.
     * 사용 시점: 잘못 작성된 회의록을 제거할 때.
     *
     * @param id        워크스페이스 ID
     * @param meetingId 회의록 ID
     */
    @DeleteMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<Void>> deleteMeeting(
            @PathVariable Long id,
            @PathVariable Long meetingId) {
        workspaceService.deleteMeeting(id, meetingId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** JWT 의 username(= userId) 로 User 엔티티를 로드하는 내부 헬퍼 */
    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    /** AttachmentRequest 목록을 서비스 레이어의 AttachmentPayload 로 변환 */
    private List<WorkspaceService.AttachmentPayload> toAttachmentPayloads(List<AttachmentRequest> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(attachment -> new WorkspaceService.AttachmentPayload(
                        attachment.getFileItemId(),
                        attachment.getStoragePath(),
                        attachment.getName(),
                        attachment.getSize()
                ))
                .collect(Collectors.toList());
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** PUT /api/workspaces/{id} 요청 바디 */
    @Getter
    static class WorkspaceRequest {
        private String name;
        private String description;
    }

    /** POST/PUT 회의록 요청 바디 */
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

    /** 첨부파일 요청 DTO */
    @Getter
    static class AttachmentRequest {
        private Long fileItemId;
        private String storagePath;
        private String name;
        private Long size;
    }

    /** 워크스페이스 목록 응답 DTO */
    record WorkspaceResponse(Long id, Long cohortId, String department, String name, String description,
                             long fileCount, long meetingCount, LocalDateTime updatedAt) {
        static WorkspaceResponse of(WorkspaceService.WorkspaceSummary summary) {
            Workspace workspace = summary.workspace();
            return new WorkspaceResponse(
                    workspace.getId(),
                    workspace.getCohortId(),
                    workspace.getDepartment() != null ? workspace.getDepartment().name() : "전체",
                    workspace.getName(),
                    workspace.getDescription(),
                    summary.fileCount(),
                    summary.meetingCount(),
                    workspace.getUpdatedAt()
            );
        }
    }

    /** 워크스페이스 상세 응답 DTO (워크스페이스 + 회의록 목록) */
    record WorkspaceDetailResponse(WorkspaceResponse workspace, List<MeetingResponse> meetings) {
        static WorkspaceDetailResponse of(WorkspaceService.WorkspaceDetail detail) {
            Workspace workspace = detail.workspace();
            return new WorkspaceDetailResponse(
                    new WorkspaceResponse(
                            workspace.getId(),
                            workspace.getCohortId(),
                            workspace.getDepartment() != null ? workspace.getDepartment().name() : "전체",
                            workspace.getName(),
                            workspace.getDescription(),
                            detail.fileCount(),
                            detail.meetings().size(),
                            workspace.getUpdatedAt()
                    ),
                    detail.meetings().stream().map(MeetingResponse::of).collect(Collectors.toList())
            );
        }
    }

    /** 회의록 응답 DTO (첨부파일 포함) */
    record MeetingResponse(Long id, Long workspaceId, String title, LocalDate date, List<String> attendees,
                           String agenda, String content, Long createdBy, Long eventId,
                           LocalDateTime createdAt, LocalDateTime updatedAt, List<MeetingAttachmentResponse> attachments) {
        static MeetingResponse of(WorkspaceService.MeetingSummary summary) {
            Meeting meeting = summary.meeting();
            return new MeetingResponse(
                    meeting.getId(),
                    meeting.getWorkspaceId(),
                    meeting.getTitle(),
                    meeting.getDate(),
                    meeting.getAttendees(),
                    meeting.getAgenda(),
                    meeting.getContent(),
                    meeting.getCreatedBy(),
                    meeting.getEventId(),
                    meeting.getCreatedAt(),
                    meeting.getUpdatedAt(),
                    summary.attachments().stream().map(MeetingAttachmentResponse::of).collect(Collectors.toList())
            );
        }
    }

    /** 첨부파일 응답 DTO */
    record MeetingAttachmentResponse(Long id, Long fileItemId, String storagePath, String name, Long size) {
        static MeetingAttachmentResponse of(MeetingAttachment attachment) {
            return new MeetingAttachmentResponse(
                    attachment.getId(),
                    attachment.getFileItemId(),
                    attachment.getStoragePath(),
                    attachment.getName(),
                    attachment.getSize()
            );
        }
    }
}
