package com.cowork.workspace;

import com.cowork.cohort.Cohort;
import com.cowork.cohort.CohortRepository;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.file.FileItemRepository;
import com.cowork.organization.OrganizationDepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAttachmentRepository meetingAttachmentRepository;
    private final FileItemRepository fileItemRepository;
    private final CohortRepository cohortRepository;
    private final OrganizationDepartmentService organizationDepartmentService;

    @Transactional
    public List<WorkspaceSummary> getWorkspaces(Long cohortId) {
        ensureDefaultWorkspaces(cohortId);
        return workspaceRepository.findByCohortIdOrderByCreatedAtAsc(cohortId).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkspaceDetail getWorkspaceDetail(Long workspaceId) {
        Workspace workspace = findWorkspace(workspaceId);
        return buildDetail(workspace);
    }

    @Transactional
    public WorkspaceDetail updateWorkspace(Long workspaceId, String name, String description) {
        Workspace workspace = findWorkspace(workspaceId);
        workspace.update(name, description);
        return buildDetail(workspace);
    }

    public List<MeetingSummary> getMeetings(Long workspaceId) {
        findWorkspace(workspaceId);
        return buildMeetingSummaries(meetingRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByDateDesc(workspaceId));
    }

    public MeetingDetail getMeetingDetail(Long workspaceId, Long meetingId) {
        findWorkspace(workspaceId);
        return buildMeetingDetail(findMeeting(workspaceId, meetingId));
    }

    @Transactional
    public MeetingDetail createMeeting(Long workspaceId, String title, LocalDate date, List<String> attendees,
                                       String agenda, String content, Long createdBy, Long eventId,
                                       List<AttachmentPayload> attachments) {
        findWorkspace(workspaceId);
        Meeting meeting = Meeting.builder()
                .workspaceId(workspaceId)
                .title(title)
                .date(date)
                .attendees(attendees)
                .agenda(agenda)
                .content(content)
                .createdBy(createdBy)
                .eventId(eventId)
                .build();
        meetingRepository.save(meeting);
        replaceAttachments(meeting.getId(), attachments);
        return buildMeetingDetail(meeting);
    }

    @Transactional
    public MeetingDetail updateMeeting(Long workspaceId, Long meetingId, String title, LocalDate date,
                                       List<String> attendees, String agenda, String content, Long eventId,
                                       List<AttachmentPayload> attachments) {
        Meeting meeting = findMeeting(workspaceId, meetingId);
        meeting.update(title, date, attendees, agenda, content, eventId);
        replaceAttachments(meetingId, attachments);
        return buildMeetingDetail(meeting);
    }

    @Transactional
    public void deleteMeeting(Long workspaceId, Long meetingId) {
        Meeting meeting = findMeeting(workspaceId, meetingId);
        meetingAttachmentRepository.deleteByMeetingId(meetingId);
        meeting.softDelete();
    }

    private WorkspaceDetail buildDetail(Workspace workspace) {
        List<Meeting> meetings = meetingRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByDateDesc(workspace.getId());
        List<MeetingSummary> meetingSummaries = buildMeetingSummaries(meetings);
        return new WorkspaceDetail(workspace, getFileCount(workspace), meetingSummaries);
    }

    private List<MeetingSummary> buildMeetingSummaries(List<Meeting> meetings) {
        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
        Map<Long, List<MeetingAttachment>> attachmentsByMeeting = meetingIds.isEmpty()
                ? Map.of()
                : meetingAttachmentRepository.findByMeetingIdIn(meetingIds).stream()
                .collect(Collectors.groupingBy(MeetingAttachment::getMeetingId, LinkedHashMap::new, Collectors.toList()));

        return meetings.stream()
                .map(meeting -> MeetingSummary.of(meeting, attachmentsByMeeting.getOrDefault(meeting.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private MeetingDetail buildMeetingDetail(Meeting meeting) {
        List<MeetingAttachment> attachments = meetingAttachmentRepository.findByMeetingIdIn(List.of(meeting.getId()));
        return new MeetingDetail(MeetingSummary.of(meeting, attachments));
    }

    private WorkspaceSummary toSummary(Workspace workspace) {
        return new WorkspaceSummary(
                workspace,
                getFileCount(workspace),
                meetingRepository.countByWorkspaceIdAndDeletedAtIsNull(workspace.getId())
        );
    }

    private long getFileCount(Workspace workspace) {
        if (workspace.getDepartment() == null) {
            return fileItemRepository.countByCohortIdAndDeletedAtIsNull(workspace.getCohortId());
        }
        return fileItemRepository.countByCohortIdAndDepartmentAndDeletedAtIsNull(workspace.getCohortId(), workspace.getDepartment());
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private Meeting findMeeting(Long workspaceId, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!meeting.getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(ErrorCode.MEETING_NOT_FOUND);
        }
        return meeting;
    }

    private void replaceAttachments(Long meetingId, List<AttachmentPayload> attachments) {
        meetingAttachmentRepository.deleteByMeetingId(meetingId);
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        List<MeetingAttachment> items = new ArrayList<>();
        for (AttachmentPayload attachment : attachments) {
            items.add(MeetingAttachment.builder()
                    .meetingId(meetingId)
                    .fileItemId(attachment.fileItemId())
                    .storagePath(attachment.storagePath())
                    .name(attachment.name())
                    .size(attachment.size())
                    .build());
        }
        meetingAttachmentRepository.saveAll(items);
    }

    private void ensureDefaultWorkspaces(Long cohortId) {
        List<Workspace> existing = workspaceRepository.findByCohortIdOrderByCreatedAtAsc(cohortId);
        boolean hasShared = existing.stream().anyMatch(workspace -> workspace.getDepartment() == null);
        if (!hasShared) {
            workspaceRepository.save(Workspace.builder()
                    .cohortId(cohortId)
                    .name("전체 워크스페이스")
                    .description("모든 부서가 함께 사용하는 공용 공간입니다.")
                    .build());
        }

        for (String department : resolveOrganizationDepartments(cohortId)) {
            boolean exists = existing.stream().anyMatch(workspace -> department.equals(workspace.getDepartment()));
            if (!exists) {
                workspaceRepository.save(Workspace.builder()
                        .cohortId(cohortId)
                        .department(department)
                        .name(department)
                        .description(department + " 자료와 회의록을 관리하는 공간입니다.")
                        .build());
            }
        }
    }

    private List<String> resolveOrganizationDepartments(Long cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COHORT_NOT_FOUND));
        return organizationDepartmentService.getDepartmentNames(cohort.getOrganization().getId());
    }

    public record AttachmentPayload(Long fileItemId, String storagePath, String name, Long size) {
    }

    public record WorkspaceSummary(Workspace workspace, long fileCount, long meetingCount) {
    }

    public record WorkspaceDetail(Workspace workspace, long fileCount, List<MeetingSummary> meetings) {
    }

    public record MeetingSummary(Meeting meeting, List<MeetingAttachment> attachments) {
        static MeetingSummary of(Meeting meeting, List<MeetingAttachment> attachments) {
            return new MeetingSummary(meeting, attachments);
        }
    }

    public record MeetingDetail(MeetingSummary meeting) {
    }
}
