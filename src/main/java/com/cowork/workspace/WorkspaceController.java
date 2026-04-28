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

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> getWorkspaces(@RequestParam Long cohortId) {
        List<WorkspaceResponse> workspaces = workspaceService.getWorkspaces(cohortId).stream()
                .map(WorkspaceResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(workspaces));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceDetailResponse>> getWorkspace(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(WorkspaceDetailResponse.of(workspaceService.getWorkspaceDetail(id))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceDetailResponse>> updateWorkspace(
            @PathVariable Long id,
            @RequestBody WorkspaceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                WorkspaceDetailResponse.of(workspaceService.updateWorkspace(id, request.getName(), request.getDescription()))
        ));
    }

    @GetMapping("/{id}/meetings")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getMeetings(@PathVariable Long id) {
        List<MeetingResponse> meetings = workspaceService.getMeetings(id).stream()
                .map(MeetingResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(meetings));
    }

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

    @GetMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(
            @PathVariable Long id,
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                MeetingResponse.of(workspaceService.getMeetingDetail(id, meetingId).meeting())
        ));
    }

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

    @DeleteMapping("/{id}/meetings/{meetingId}")
    public ResponseEntity<ApiResponse<Void>> deleteMeeting(
            @PathVariable Long id,
            @PathVariable Long meetingId) {
        workspaceService.deleteMeeting(id, meetingId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

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
