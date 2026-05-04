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

/**
 * 일정 조율(타임테이블) 컨트롤러 (ScheduleController)
 *
 * 역할:
 *   구성원들의 가능 시간을 수집해 공통 가능 슬롯을 찾는 "When2meet" 스타일의 API 를 제공한다.
 *   기본 경로: /api/timetables
 *
 * 전체 흐름:
 *   1. 관리자가 타임테이블 생성 (날짜범위·시간범위·슬롯단위·참여자 목록 설정)
 *   2. 참여자들이 POST /{id}/respond 로 가능 시간 제출
 *   3. GET /{id}/results 로 슬롯별 참여 인원수와 최적 슬롯 조회
 *
 * 인증 필요: /respond 를 제외한 대부분의 엔드포인트에 JWT 필요
 *            (참여자 응답은 링크 공유 방식이므로 인증 없이 접근 가능하도록 구성 가능)
 */
@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UserRepository userRepository;

    /**
     * 타임테이블 목록 조회
     *
     * 동작: cohortId 로 타임테이블 목록을 조회하며, 상태(OPEN/CLOSED)로 필터링 가능.
     *       각 항목에 참여자 수와 응답 수가 포함된다.
     * 사용 시점: 일정 조율 목록 화면에서 조율표 목록을 표시할 때.
     *
     * @param cohortId 필수. 조회할 코호트 ID
     * @param status   선택. 상태 필터 (OPEN / CLOSED)
     * @return 타임테이블 요약 목록 (참여자 수·응답 수 포함)
     */
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

    /**
     * 타임테이블 생성
     *
     * 동작: 타임테이블과 참여자 목록을 함께 저장한다.
     *       생성자(createdBy)는 JWT 토큰에서 자동으로 설정된다.
     * 사용 시점: 새 일정 조율이 필요할 때 관리자가 생성.
     *
     * @param request     타임테이블 정보 + participants 이름 목록
     * @param userDetails 현재 로그인 사용자 (생성자)
     * @return 생성된 타임테이블 상세 정보 (참여자 목록 포함)
     */
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

    /**
     * 타임테이블 상세 조회
     *
     * 동작: 타임테이블 기본 정보 + 참여자 목록 + 각 참여자의 응답 목록을 반환한다.
     * 사용 시점: 타임테이블 상세 화면에서 전체 현황을 볼 때.
     *
     * @param id 타임테이블 ID
     * @return 타임테이블 + 참여자 목록 + 응답 목록
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> getTimetable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(TimetableDetailResponse.of(scheduleService.getTimetableDetail(id))));
    }

    /**
     * 타임테이블 수정
     *
     * 동작: 타임테이블 기본 정보와 참여자 목록을 수정한다.
     *       기존 참여자 목록은 삭제 후 재삽입 방식으로 교체된다.
     * 사용 시점: 날짜범위·시간범위·참여자 변경 등이 필요할 때.
     *
     * @param id      타임테이블 ID
     * @param request 수정할 정보
     * @return 수정된 타임테이블 상세 정보
     */
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

    /**
     * 타임테이블 상태 변경 (OPEN ↔ CLOSED)
     *
     * 동작: 타임테이블의 status 를 변경한다.
     *       CLOSED 로 변경하면 더 이상 응답 제출이 불가해야 한다 (서비스 레이어에서 검증).
     * 사용 시점: 응답 수집을 마감하거나 다시 열 때.
     *
     * @param id   타임테이블 ID
     * @param body { "status": "CLOSED" }
     * @return 업데이트된 타임테이블 요약 (참여자 수·응답 수 포함)
     */
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

    /**
     * 타임테이블 삭제
     *
     * 동작: 타임테이블과 연관된 참여자 목록, 응답 목록을 모두 삭제한다.
     * 사용 시점: 잘못 만든 조율표를 제거할 때.
     *
     * @param id 타임테이블 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTimetable(@PathVariable Long id) {
        scheduleService.deleteTimetable(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 가능 시간 응답 제출
     *
     * 동작:
     *   1. 참여자 이름으로 TimetableParticipant 조회
     *   2. 이미 응답한 경우 TimetableSubmission 의 selectedSlots 를 갱신.
     *      처음 응답하는 경우 새 TimetableSubmission 생성 + TimetableParticipant.markResponded() 호출.
     *   3. 슬롯은 "날짜 시간" 포맷으로 직렬화하여 JSON 배열로 저장.
     *
     * 사용 시점: 참여자가 공유 링크로 접속하여 가능 시간을 선택·제출할 때.
     *
     * @param id      타임테이블 ID
     * @param request { participantName, availableSlots: [{ date, time }] }
     * @return 제출된 응답 정보
     */
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

    /**
     * 일정 조율 결과 조회
     *
     * 동작: 모든 응답을 집계하여 슬롯별 참여 인원수와 최적 슬롯(최다 참여) 목록을 반환.
     * 사용 시점: 결과 화면에서 공통 가능 시간대를 확인할 때.
     *
     * @param id 타임테이블 ID
     * @return { timetableId, title, responseCount, slotCounts[], bestSlots[] }
     */
    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<TimetableResultResponse>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(TimetableResultResponse.of(scheduleService.getResults(id))));
    }

    /** JWT 의 username(= userId) 로 User 엔티티를 로드하는 내부 헬퍼 */
    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** POST/PUT 타임테이블 요청 바디 */
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
        /** 참여자 이름 목록 (예: ["홍길동", "이철수"]) */
        private List<String> participants;
    }

    /** POST /{id}/respond 요청 바디 */
    @Getter
    static class TimetableRespondRequest {
        private String participantName;
        /** 참여자가 가능하다고 선택한 시간 슬롯 목록 */
        private List<TimeSlotRequest> availableSlots;
    }

    /** 개별 시간 슬롯 요청 (날짜 + 시간 분리) */
    @Getter
    static class TimeSlotRequest {
        private String date; // "2025-03-10"
        private String time; // "09:00"
    }

    /** 타임테이블 요약 응답 DTO (목록용) */
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

    /** 타임테이블 상세 응답 DTO (참여자 목록 + 응답 목록 포함) */
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

    /** 참여자 응답 DTO */
    record ParticipantResponse(Long id, String name, boolean responded) {
        static ParticipantResponse of(TimetableParticipant participant) {
            return new ParticipantResponse(participant.getId(), participant.getName(), participant.isResponded());
        }
    }

    /** 개별 응답 제출 결과 DTO */
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

    /** 슬롯 응답 DTO — "날짜 시간" 형식의 문자열을 date/time 으로 분리 */
    record SlotResponse(String date, String time) {
        static SlotResponse of(String serialized) {
            String[] parts = serialized.split(" ", 2);
            return new SlotResponse(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }

    /** 조율 결과 응답 DTO (슬롯별 참여 인원수 + 최적 슬롯) */
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

    /** 슬롯별 참여 인원수 DTO */
    record SlotCountResponse(String date, String time, long count) {
        static SlotCountResponse of(ScheduleService.SlotCount slotCount) {
            return new SlotCountResponse(slotCount.date(), slotCount.time(), slotCount.count());
        }
    }
}
