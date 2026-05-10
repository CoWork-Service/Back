package com.cowork.schedule;

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

@Tag(name = "Schedule (Timetable)", description = "일정 조율 API — When2meet 스타일의 가능 시간 수집 및 최적 슬롯 계산")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UserRepository userRepository;

    @Operation(
            summary = "타임테이블 목록 조회",
            description = """
                    코호트의 일정 조율표(타임테이블) 목록을 조회합니다.

                    **사용 시점:** 일정 조율 목록 화면에서 조율표 목록을 표시할 때.

                    각 항목에 **참여자 수**와 **응답 수**가 포함됩니다.

                    **상태(status) 값:** `OPEN`(응답 수집 중) / `CLOSED`(마감)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "타임테이블 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "title": "MT 날짜 조율",
                                          "description": "3월 MT 날짜를 정해주세요",
                                          "dateRangeStart": "2025-03-10",
                                          "dateRangeEnd": "2025-03-20",
                                          "timeRangeStart": "09:00:00",
                                          "timeRangeEnd": "22:00:00",
                                          "slotMinutes": 60,
                                          "status": "CLOSED",
                                          "createdBy": 1,
                                          "eventId": 1,
                                          "participantCount": 15,
                                          "responseCount": 12,
                                          "createdAt": "2025-02-01T09:00:00",
                                          "updatedAt": "2025-02-10T15:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<TimetableSummaryResponse>>> getTimetables(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "상태 필터 (OPEN / CLOSED)", example = "OPEN") @RequestParam(required = false) TimetableStatus status) {
        List<TimetableSummaryResponse> timetables = scheduleService.getTimetables(cohortId, status).stream()
                .map(timetable -> TimetableSummaryResponse.of(
                        timetable,
                        scheduleService.getParticipantCount(timetable.getId()),
                        scheduleService.getResponseCount(timetable.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(timetables));
    }

    @Operation(
            summary = "타임테이블 생성",
            description = """
                    일정 조율표를 생성합니다. 참여자 목록을 함께 설정합니다.

                    **사용 시점:** 새 일정 조율이 필요할 때 관리자가 생성.

                    **슬롯 단위(slotMinutes):** 시간 선택 단위 (예: 30 = 30분 단위, 60 = 1시간 단위)

                    **participants:** 참여자 이름 목록 (예: ["홍길동", "이철수", "박지훈"])
                    참여자들은 `POST /{id}/respond`로 각자의 가능 시간을 제출합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "타임테이블 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "timetable": {
                                          "id": 2, "title": "종강 파티 날짜 조율", "status": "OPEN",
                                          "dateRangeStart": "2025-06-18", "dateRangeEnd": "2025-06-22",
                                          "timeRangeStart": "18:00:00", "timeRangeEnd": "23:00:00",
                                          "slotMinutes": 60, "participantCount": 3, "responseCount": 0
                                        },
                                        "participants": [
                                          { "id": 1, "name": "홍길동", "responded": false },
                                          { "id": 2, "name": "이철수", "responded": false },
                                          { "id": 3, "name": "박지훈", "responded": false }
                                        ],
                                        "responses": []
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> createTimetable(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "타임테이블 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "cohortId": 5,
                              "title": "종강 파티 날짜 조율",
                              "description": "6월 종강 파티 날짜를 정해주세요",
                              "dateRangeStart": "2025-06-18",
                              "dateRangeEnd": "2025-06-22",
                              "timeRangeStart": "18:00",
                              "timeRangeEnd": "23:00",
                              "slotMinutes": 60,
                              "status": "OPEN",
                              "eventId": 2,
                              "participants": ["홍길동", "이철수", "박지훈"]
                            }
                            """)))
            @RequestBody TimetableRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        ScheduleService.TimetableDetail detail = scheduleService.createTimetable(
                request.getCohortId(), request.getTitle(), request.getDescription(),
                request.getDateRangeStart(), request.getDateRangeEnd(),
                request.getTimeRangeStart(), request.getTimeRangeEnd(),
                request.getSlotMinutes(), request.getStatus(), user.getId(),
                request.getEventId(), request.getParticipants()
        );
        return ResponseEntity.ok(ApiResponse.ok(TimetableDetailResponse.of(detail)));
    }

    @Operation(
            summary = "타임테이블 상세 조회",
            description = """
                    타임테이블 기본 정보 + 참여자 목록 + 각 참여자의 응답 목록을 반환합니다.

                    **사용 시점:** 타임테이블 상세 화면에서 전체 현황을 볼 때.

                    `participants[].responded`가 `true`이면 해당 참여자가 응답을 완료한 상태입니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "타임테이블 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "timetable": { "id": 2, "title": "종강 파티 날짜 조율", "status": "OPEN", "participantCount": 3, "responseCount": 1 },
                                        "participants": [
                                          { "id": 1, "name": "홍길동", "responded": true },
                                          { "id": 2, "name": "이철수", "responded": false },
                                          { "id": 3, "name": "박지훈", "responded": false }
                                        ],
                                        "responses": [
                                          {
                                            "id": 1, "participantId": 1, "participantName": "홍길동",
                                            "selectedSlots": [
                                              { "date": "2025-06-18", "time": "18:00" },
                                              { "date": "2025-06-18", "time": "19:00" },
                                              { "date": "2025-06-19", "time": "18:00" }
                                            ],
                                            "submittedAt": "2025-05-10T10:30:00"
                                          }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "타임테이블을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> getTimetable(
            @Parameter(description = "타임테이블 ID", required = true, example = "2") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(TimetableDetailResponse.of(scheduleService.getTimetableDetail(id))));
    }

    @Operation(
            summary = "타임테이블 수정",
            description = """
                    타임테이블 기본 정보와 참여자 목록을 수정합니다.

                    **사용 시점:** 날짜범위·시간범위·참여자 변경 등이 필요할 때.

                    기존 참여자 목록은 삭제 후 재삽입 방식으로 교체됩니다.
                    기존 응답 데이터가 무효화될 수 있으므로 주의가 필요합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "타임테이블 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "타임테이블을 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> updateTimetable(
            @Parameter(description = "타임테이블 ID", required = true, example = "2") @PathVariable Long id,
            @RequestBody TimetableRequest request) {
        ScheduleService.TimetableDetail detail = scheduleService.updateTimetable(
                id, request.getTitle(), request.getDescription(),
                request.getDateRangeStart(), request.getDateRangeEnd(),
                request.getTimeRangeStart(), request.getTimeRangeEnd(),
                request.getSlotMinutes(), request.getStatus(), request.getEventId(),
                request.getParticipants()
        );
        return ResponseEntity.ok(ApiResponse.ok(TimetableDetailResponse.of(detail)));
    }

    @Operation(
            summary = "타임테이블 상태 변경 (OPEN ↔ CLOSED)",
            description = """
                    타임테이블의 응답 수집 상태를 변경합니다.

                    **사용 시점:** 응답 수집을 마감하거나 다시 열 때.

                    **status 값:**
                    - `OPEN` → 응답 수집 중
                    - `CLOSED` → 마감 (추가 응답 불가)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 2,
                                        "title": "종강 파티 날짜 조율",
                                        "status": "CLOSED",
                                        "participantCount": 3,
                                        "responseCount": 3
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "타임테이블을 찾을 수 없음")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TimetableSummaryResponse>> updateStatus(
            @Parameter(description = "타임테이블 ID", required = true, example = "2") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "상태 변경 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "status": "CLOSED" }
                            """)))
            @RequestBody Map<String, String> body) {
        Timetable timetable = scheduleService.updateStatus(id, TimetableStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(
                TimetableSummaryResponse.of(timetable,
                        scheduleService.getParticipantCount(id),
                        scheduleService.getResponseCount(id))
        ));
    }

    @Operation(
            summary = "타임테이블 삭제",
            description = """
                    타임테이블과 연관된 참여자 목록, 응답 목록을 모두 삭제합니다.

                    **사용 시점:** 잘못 만든 조율표를 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "타임테이블 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "타임테이블을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTimetable(
            @Parameter(description = "타임테이블 ID", required = true, example = "2") @PathVariable Long id) {
        scheduleService.deleteTimetable(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "가능 시간 응답 제출 (인증 불필요)",
            description = """
                    참여자가 자신의 가능 시간을 제출합니다.

                    **사용 시점:** 참여자가 공유 링크로 접속하여 가능 시간을 선택·제출할 때.

                    **인증 불필요** — 공유 링크 방식으로 동작합니다.

                    **처리 방식:**
                    - 이미 응답한 경우 기존 응답을 **갱신**합니다.
                    - 처음 응답하는 경우 **새 응답**을 생성합니다.

                    **슬롯 형식:** `{ "date": "2025-06-18", "time": "18:00" }`
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "응답 제출 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 3,
                                        "participantId": 2,
                                        "participantName": "이철수",
                                        "selectedSlots": [
                                          { "date": "2025-06-18", "time": "18:00" },
                                          { "date": "2025-06-19", "time": "19:00" }
                                        ],
                                        "submittedAt": "2025-05-10T11:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "참여자 이름을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "참여자를 찾을 수 없습니다.",
                                      "code": "PARTICIPANT_NOT_FOUND"
                                    }
                                    """)))
    })
    @PostMapping("/{id}/respond")
    public ResponseEntity<ApiResponse<TimetableSubmissionResponse>> respond(
            @Parameter(description = "타임테이블 ID", required = true, example = "2") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "가능 시간 응답 제출 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "participantName": "이철수",
                              "availableSlots": [
                                { "date": "2025-06-18", "time": "18:00" },
                                { "date": "2025-06-18", "time": "19:00" },
                                { "date": "2025-06-19", "time": "18:00" },
                                { "date": "2025-06-19", "time": "19:00" }
                              ]
                            }
                            """)))
            @RequestBody TimetableRespondRequest request) {
        TimetableSubmission submission = scheduleService.respond(
                id, request.getParticipantName(),
                request.getAvailableSlots() == null ? List.of() : request.getAvailableSlots().stream()
                        .map(slot -> new ScheduleService.TimeSlotPayload(slot.getDate(), slot.getTime()))
                        .collect(Collectors.toList())
        );
        return ResponseEntity.ok(ApiResponse.ok(TimetableSubmissionResponse.of(submission, request.getParticipantName())));
    }

    @Operation(
            summary = "일정 조율 결과 조회",
            description = """
                    모든 응답을 집계하여 슬롯별 참여 인원수와 최적 슬롯을 반환합니다.

                    **사용 시점:** 결과 화면에서 공통 가능 시간대를 확인할 때.

                    **bestSlots:** 가장 많은 참여자가 선택한 슬롯들 (공동 최다 포함)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "결과 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "timetableId": 2,
                                        "title": "종강 파티 날짜 조율",
                                        "responseCount": 3,
                                        "slotCounts": [
                                          { "date": "2025-06-18", "time": "18:00", "count": 3 },
                                          { "date": "2025-06-18", "time": "19:00", "count": 2 },
                                          { "date": "2025-06-19", "time": "18:00", "count": 1 }
                                        ],
                                        "bestSlots": [
                                          { "date": "2025-06-18", "time": "18:00", "count": 3 }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<TimetableResultResponse>> getResults(
            @Parameter(description = "타임테이블 ID", required = true, example = "2") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(TimetableResultResponse.of(scheduleService.getResults(id))));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

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
                    timetable.getId(), timetable.getCohortId(), timetable.getTitle(),
                    timetable.getDescription(), timetable.getDateRangeStart(), timetable.getDateRangeEnd(),
                    timetable.getTimeRangeStart(), timetable.getTimeRangeEnd(), timetable.getSlotMinutes(),
                    timetable.getStatus().name(), timetable.getCreatedBy(), timetable.getEventId(),
                    participantCount, responseCount, timetable.getCreatedAt(), timetable.getUpdatedAt()
            );
        }

        static TimetableSummaryResponse of(ScheduleService.TimetableDetail detail) {
            Timetable timetable = detail.timetable();
            return new TimetableSummaryResponse(
                    timetable.getId(), timetable.getCohortId(), timetable.getTitle(),
                    timetable.getDescription(), timetable.getDateRangeStart(), timetable.getDateRangeEnd(),
                    timetable.getTimeRangeStart(), timetable.getTimeRangeEnd(), timetable.getSlotMinutes(),
                    timetable.getStatus().name(), timetable.getCreatedBy(), timetable.getEventId(),
                    detail.participants().size(), detail.submissions().size(),
                    timetable.getCreatedAt(), timetable.getUpdatedAt()
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
                    submission.getId(), submission.getParticipantId(), participantName,
                    submission.getSelectedSlots().stream().map(SlotResponse::of).collect(Collectors.toList()),
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
                    result.detail().timetable().getId(), result.detail().timetable().getTitle(),
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
