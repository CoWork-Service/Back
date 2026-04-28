package com.cowork.schedule;

import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TimetableSummaryResponse>>> getTimetables(
            @RequestParam Long cohortId,
            @RequestParam(required = false) TimetableStatus status) {
        List<TimetableSummaryResponse> timetables = scheduleService.getTimetables(cohortId, status).stream()
                .map(timetable -> TimetableSummaryResponse.of(
                        timetable,
                        scheduleService.getParticipantCount(timetable.getId()),
                        scheduleService.getResponseCount(timetable.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(timetables));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> createTimetable(
            @RequestBody TimetableRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        ScheduleService.TimetableDetail detail = scheduleService.createTimetable(
                request.getCohortId(),
                request.getTitle(),
                request.getDescription(),
                request.getDateRangeStart(),
                request.getDateRangeEnd(),
                request.getTimeRangeStart(),
                request.getTimeRangeEnd(),
                request.getSlotMinutes(),
                request.getStatus(),
                user.getId(),
                request.getEventId(),
                request.getParticipants()
        );
        return ResponseEntity.ok(ApiResponse.ok(TimetableDetailResponse.of(detail)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> getTimetable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(TimetableDetailResponse.of(scheduleService.getTimetableDetail(id))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> updateTimetable(
            @PathVariable Long id,
            @RequestBody TimetableRequest request) {
        ScheduleService.TimetableDetail detail = scheduleService.updateTimetable(
                id,
                request.getTitle(),
                request.getDescription(),
                request.getDateRangeStart(),
                request.getDateRangeEnd(),
                request.getTimeRangeStart(),
                request.getTimeRangeEnd(),
                request.getSlotMinutes(),
                request.getStatus(),
                request.getEventId(),
                request.getParticipants()
        );
        return ResponseEntity.ok(ApiResponse.ok(TimetableDetailResponse.of(detail)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TimetableSummaryResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Timetable timetable = scheduleService.updateStatus(id, TimetableStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(
                TimetableSummaryResponse.of(
                        timetable,
                        scheduleService.getParticipantCount(id),
                        scheduleService.getResponseCount(id))
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTimetable(@PathVariable Long id) {
        scheduleService.deleteTimetable(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<ApiResponse<TimetableSubmissionResponse>> respond(
            @PathVariable Long id,
            @RequestBody TimetableRespondRequest request) {
        TimetableSubmission submission = scheduleService.respond(
                id,
                request.getParticipantName(),
                request.getAvailableSlots() == null ? List.of() : request.getAvailableSlots().stream()
                        .map(slot -> new ScheduleService.TimeSlotPayload(slot.getDate(), slot.getTime()))
                        .collect(Collectors.toList())
        );
        return ResponseEntity.ok(ApiResponse.ok(TimetableSubmissionResponse.of(submission, request.getParticipantName())));
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<TimetableResultResponse>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(TimetableResultResponse.of(scheduleService.getResults(id))));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    @Getter
    static class TimetableRequest {
        private Long cohortId;
        private String title;
        private String description;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateRangeStart;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateRangeEnd;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime timeRangeStart;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime timeRangeEnd;
        private Integer slotMinutes;
        private TimetableStatus status;
        private Long eventId;
        private List<String> participants;
    }

    @Getter
    static class TimetableRespondRequest {
        private String participantName;
        private List<TimeSlotRequest> availableSlots;
    }

    @Getter
    static class TimeSlotRequest {
        private String date;
        private String time;
    }

    record TimetableSummaryResponse(Long id, Long cohortId, String title, String description,
                                    LocalDate dateRangeStart, LocalDate dateRangeEnd,
                                    LocalTime timeRangeStart, LocalTime timeRangeEnd,
                                    Integer slotMinutes, String status, Long createdBy, Long eventId,
                                    long participantCount, long responseCount,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        static TimetableSummaryResponse of(Timetable timetable, long participantCount, long responseCount) {
            return new TimetableSummaryResponse(
                    timetable.getId(),
                    timetable.getCohortId(),
                    timetable.getTitle(),
                    timetable.getDescription(),
                    timetable.getDateRangeStart(),
                    timetable.getDateRangeEnd(),
                    timetable.getTimeRangeStart(),
                    timetable.getTimeRangeEnd(),
                    timetable.getSlotMinutes(),
                    timetable.getStatus().name(),
                    timetable.getCreatedBy(),
                    timetable.getEventId(),
                    participantCount,
                    responseCount,
                    timetable.getCreatedAt(),
                    timetable.getUpdatedAt()
            );
        }

        static TimetableSummaryResponse of(ScheduleService.TimetableDetail detail) {
            Timetable timetable = detail.timetable();
            return new TimetableSummaryResponse(
                    timetable.getId(),
                    timetable.getCohortId(),
                    timetable.getTitle(),
                    timetable.getDescription(),
                    timetable.getDateRangeStart(),
                    timetable.getDateRangeEnd(),
                    timetable.getTimeRangeStart(),
                    timetable.getTimeRangeEnd(),
                    timetable.getSlotMinutes(),
                    timetable.getStatus().name(),
                    timetable.getCreatedBy(),
                    timetable.getEventId(),
                    detail.participants().size(),
                    detail.submissions().size(),
                    timetable.getCreatedAt(),
                    timetable.getUpdatedAt()
            );
        }
    }

    record TimetableDetailResponse(TimetableSummaryResponse timetable,
                                   List<ParticipantResponse> participants,
                                   List<TimetableSubmissionResponse> responses) {
        static TimetableDetailResponse of(ScheduleService.TimetableDetail detail) {
            return new TimetableDetailResponse(
                    TimetableSummaryResponse.of(detail),
                    detail.participants().stream().map(ParticipantResponse::of).collect(Collectors.toList()),
                    detail.submissions().stream()
                            .map(submission -> TimetableSubmissionResponse.of(
                                    submission,
                                    detail.participantsById().get(submission.getParticipantId()).getName()
                            ))
                            .collect(Collectors.toList())
            );
        }
    }

    record ParticipantResponse(Long id, String name, boolean responded) {
        static ParticipantResponse of(TimetableParticipant participant) {
            return new ParticipantResponse(participant.getId(), participant.getName(), participant.isResponded());
        }
    }

    record TimetableSubmissionResponse(Long id, Long participantId, String participantName,
                                       List<SlotResponse> selectedSlots, LocalDateTime submittedAt) {
        static TimetableSubmissionResponse of(TimetableSubmission submission, String participantName) {
            return new TimetableSubmissionResponse(
                    submission.getId(),
                    submission.getParticipantId(),
                    participantName,
                    submission.getSelectedSlots().stream()
                            .map(SlotResponse::of)
                            .collect(Collectors.toList()),
                    submission.getSubmittedAt()
            );
        }
    }

    record SlotResponse(String date, String time) {
        static SlotResponse of(String serialized) {
            String[] parts = serialized.split(" ", 2);
            return new SlotResponse(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }

    record TimetableResultResponse(Long timetableId, String title, long responseCount,
                                   List<SlotCountResponse> slotCounts, List<SlotCountResponse> bestSlots) {
        static TimetableResultResponse of(ScheduleService.TimetableResult result) {
            return new TimetableResultResponse(
                    result.detail().timetable().getId(),
                    result.detail().timetable().getTitle(),
                    result.detail().submissions().size(),
                    result.slotCounts().stream().map(SlotCountResponse::of).collect(Collectors.toList()),
                    result.bestSlots().stream().map(SlotCountResponse::of).collect(Collectors.toList())
            );
        }
    }

    record SlotCountResponse(String date, String time, long count) {
        static SlotCountResponse of(ScheduleService.SlotCount slotCount) {
            return new SlotCountResponse(slotCount.date(), slotCount.time(), slotCount.count());
        }
    }
}
