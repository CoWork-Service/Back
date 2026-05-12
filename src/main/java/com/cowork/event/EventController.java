package com.cowork.event;

import com.cowork.budget.Expense;
import com.cowork.common.ApiResponse;
import com.cowork.schedule.Timetable;
import com.cowork.survey.Survey;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Event", description = "행사 관리 API — 행사 CRUD, 사진 업로드/삭제, 연관 지출·설문·타임테이블 조회")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserRepository userRepository;

    @Operation(
            summary = "행사 목록 조회",
            description = """
                    코호트의 행사 목록을 조회합니다.

                    **사용 시점:** 행사 목록 화면 또는 캘린더에서 행사를 표시할 때.

                    **상태(status) 값:**
                    - `PLANNING` — 기획 중
                    - `IN_PROGRESS` — 진행 중
                    - `COMPLETED` — 완료
                    - `CANCELLED` — 취소
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "행사 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "name": "2025 MT",
                                          "category": "MT",
                                          "status": "PLANNING",
                                          "description": "3월 엠티 일정",
                                          "startDate": "2025-03-14",
                                          "endDate": "2025-03-15",
                                          "location": "가평",
                                          "leadDepartment": "PLANNING",
                                          "organizers": ["홍길동", "이철수"],
                                          "budget": 1500000,
                                          "coverColor": "#3B82F6",
                                          "createdBy": 1,
                                          "createdAt": "2025-02-20T10:00:00",
                                          "updatedAt": "2025-02-20T10:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventSummaryResponse>>> getEvents(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "행사 상태 필터 (PLANNING / IN_PROGRESS / COMPLETED / CANCELLED)", example = "PLANNING") @RequestParam(required = false) EventStatus status,
            @Parameter(description = "분류 필터 (예: MT, 축제, 발표회)", example = "MT") @RequestParam(required = false) String category) {
        List<EventSummaryResponse> events = eventService.getEvents(cohortId, status, category).stream()
                .map(EventSummaryResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    @Operation(
            summary = "행사 생성",
            description = """
                    새 행사를 생성합니다. 생성자는 JWT 토큰에서 자동으로 설정됩니다.

                    **사용 시점:** 새 행사를 기획할 때.

                    **상태(status) 초기값:** `PLANNING` 권장

                    **부서(leadDepartment) 값:** PLANNING / MARKETING / OPERATION / FINANCE / GENERAL
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "행사 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "event": {
                                          "id": 2,
                                          "cohortId": 5,
                                          "name": "종강 파티",
                                          "category": "파티",
                                          "status": "PLANNING",
                                          "description": "1학기 종강 기념 파티",
                                          "startDate": "2025-06-20",
                                          "endDate": "2025-06-20",
                                          "location": "동아리방",
                                          "leadDepartment": "OPERATION",
                                          "organizers": ["박지훈"],
                                          "budget": 300000,
                                          "coverColor": "#F59E0B",
                                          "createdBy": 1,
                                          "createdAt": "2025-05-10T09:00:00",
                                          "updatedAt": "2025-05-10T09:00:00"
                                        },
                                        "photos": [],
                                        "expenses": [],
                                        "surveys": [],
                                        "timetables": []
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<EventDetailResponse>> createEvent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "행사 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "cohortId": 5,
                              "name": "종강 파티",
                              "category": "파티",
                              "status": "PLANNING",
                              "description": "1학기 종강 기념 파티",
                              "startDate": "2025-06-20",
                              "endDate": "2025-06-20",
                              "location": "동아리방",
                              "leadDepartment": "OPERATION",
                              "organizers": ["박지훈"],
                              "budget": 300000,
                              "coverColor": "#F59E0B"
                            }
                            """)))
            @RequestBody EventRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        EventService.EventDetail detail = eventService.createEvent(
                request.getCohortId(), request.getName(), request.getCategory(),
                request.getStatus(), request.getDescription(), request.getStartDate(),
                request.getEndDate(), request.getLocation(), request.getLeadDepartment(),
                request.getOrganizers(), request.getBudget(), request.getCoverColor(), user.getId()
        );
        return ResponseEntity.ok(ApiResponse.ok(EventDetailResponse.of(detail)));
    }

    @Operation(
            summary = "행사 상세 조회",
            description = """
                    행사 기본 정보 + 사진 + 지출 내역 + 설문 + 타임테이블을 한 번에 반환합니다.

                    **사용 시점:** 행사 상세 화면에서 전체 정보를 한 번의 요청으로 불러올 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "행사 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "event": { "id": 1, "name": "2025 MT", "status": "IN_PROGRESS", "..." : "..." },
                                        "photos": [
                                          { "id": 3, "storagePath": "events/mt_photo1.jpg", "photoUrl": "/uploads/events/mt_photo1.jpg", "caption": "출발", "tag": "현장", "uploadedBy": 1, "uploadedAt": "2025-03-14T09:00:00" }
                                        ],
                                        "expenses": [
                                          { "id": 1, "date": "2025-03-14", "category": "식비", "vendor": "편의점", "amount": 50000 }
                                        ],
                                        "surveys": [
                                          { "id": 2, "title": "MT 만족도 조사", "status": "OPEN", "createdBy": 1, "createdAt": "2025-03-16T10:00:00" }
                                        ],
                                        "timetables": [
                                          { "id": 1, "title": "MT 날짜 조율", "status": "CLOSED", "createdBy": 1, "createdAt": "2025-02-01T09:00:00" }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "행사를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> getEvent(
            @Parameter(description = "행사 ID", required = true, example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(EventDetailResponse.of(eventService.getEventDetail(id))));
    }

    @Operation(
            summary = "행사 정보 수정",
            description = """
                    행사의 기본 정보를 수정합니다 (상태 변경 포함).

                    **사용 시점:** 행사 편집 폼에서 저장하거나 상태를 변경할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "행사 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "행사를 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> updateEvent(
            @Parameter(description = "행사 ID", required = true, example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "행사 수정 요청 (cohortId 제외)",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "name": "2025 MT (확정)",
                              "category": "MT",
                              "status": "IN_PROGRESS",
                              "description": "3월 엠티 확정",
                              "startDate": "2025-03-14",
                              "endDate": "2025-03-15",
                              "location": "가평 리버빌리지",
                              "leadDepartment": "PLANNING",
                              "organizers": ["홍길동", "이철수"],
                              "budget": 1800000,
                              "coverColor": "#3B82F6"
                            }
                            """)))
            @RequestBody EventRequest request) {
        EventService.EventDetail detail = eventService.updateEvent(
                id, request.getName(), request.getCategory(), request.getStatus(),
                request.getDescription(), request.getStartDate(), request.getEndDate(),
                request.getLocation(), request.getLeadDepartment(), request.getOrganizers(),
                request.getBudget(), request.getCoverColor()
        );
        return ResponseEntity.ok(ApiResponse.ok(EventDetailResponse.of(detail)));
    }

    @Operation(
            summary = "행사 삭제",
            description = """
                    행사와 연관된 사진 파일 및 레코드를 삭제합니다.

                    **사용 시점:** 취소된 행사를 완전히 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "행사 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "행사를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @Parameter(description = "행사 ID", required = true, example = "1") @PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "행사 사진 업로드",
            description = """
                    행사에 사진을 추가합니다.

                    **사용 시점:** 행사 진행 중 또는 후 사진을 등록할 때.

                    **요청 형식:** `multipart/form-data`

                    업로드 후 사진 URL은 `photoUrl` 필드에 포함됩니다 (`/uploads/{storagePath}`).

                    **tag 예시:** "현장", "준비", "기타"
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사진 업로드 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 5,
                                        "storagePath": "events/mt_photo2_20250314.jpg",
                                        "photoUrl": "/uploads/events/mt_photo2_20250314.jpg",
                                        "caption": "저녁 식사",
                                        "tag": "현장",
                                        "uploadedBy": 1,
                                        "uploadedAt": "2025-03-14T19:30:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping("/{id}/photos")
    public ResponseEntity<ApiResponse<EventPhotoResponse>> addPhoto(
            @Parameter(description = "행사 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "사진 파일 (이미지)", required = true) @RequestParam("photo") MultipartFile photo,
            @Parameter(description = "사진 설명", example = "저녁 식사") @RequestParam(required = false) String caption,
            @Parameter(description = "사진 태그 (예: 현장, 준비, 기타)", example = "현장") @RequestParam(required = false) String tag,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        EventPhoto item = eventService.addPhoto(id, photo, caption, tag, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(EventPhotoResponse.of(item)));
    }

    @Operation(
            summary = "행사 사진 삭제",
            description = """
                    사진 파일을 스토리지에서 삭제하고 레코드를 제거합니다.

                    **사용 시점:** 잘못 업로드된 사진이나 부적절한 사진을 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사진 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사진을 찾을 수 없음")
    })
    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @Parameter(description = "행사 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "삭제할 사진 ID", required = true, example = "5") @PathVariable Long photoId) {
        eventService.deletePhoto(id, photoId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    @Getter
    static class EventRequest {
        private Long cohortId;
        private String name;
        private String category;
        private EventStatus status;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private String location;
        private String leadDepartment;
        private List<String> organizers;
        private Long budget;
        private String coverColor;
    }

    record EventSummaryResponse(Long id, Long cohortId, String name, String category, String status,
                                String description, LocalDate startDate, LocalDate endDate, String location,
                                String leadDepartment, List<String> organizers, Long budget, String coverColor,
                                Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static EventSummaryResponse of(CoworkEvent event) {
            return new EventSummaryResponse(
                    event.getId(), event.getCohortId(), event.getName(), event.getCategory(),
                    event.getStatus().toJson(), event.getDescription(), event.getStartDate(),
                    event.getEndDate(), event.getLocation(),
                    event.getLeadDepartment(),
                    event.getOrganizers(), event.getBudget(), event.getCoverColor(),
                    event.getCreatedBy(), event.getCreatedAt(), event.getUpdatedAt()
            );
        }
    }

    record EventDetailResponse(EventSummaryResponse event, List<EventPhotoResponse> photos,
                               List<ExpenseSummaryResponse> expenses,
                               List<LinkedSurveyResponse> surveys,
                               List<LinkedTimetableResponse> timetables) {
        static EventDetailResponse of(EventService.EventDetail detail) {
            return new EventDetailResponse(
                    EventSummaryResponse.of(detail.event()),
                    detail.photos().stream().map(EventPhotoResponse::of).collect(Collectors.toList()),
                    detail.expenses().stream().map(ExpenseSummaryResponse::of).collect(Collectors.toList()),
                    detail.surveys().stream().map(LinkedSurveyResponse::of).collect(Collectors.toList()),
                    detail.timetables().stream().map(LinkedTimetableResponse::of).collect(Collectors.toList())
            );
        }
    }

    record EventPhotoResponse(Long id, String storagePath, String photoUrl, String url, String caption, String tag,
                              Long uploadedBy, LocalDateTime uploadedAt) {
        static EventPhotoResponse of(EventPhoto photo) {
            String photoUrl = "/uploads/" + photo.getStoragePath();
            return new EventPhotoResponse(
                    photo.getId(), photo.getStoragePath(), photoUrl, photoUrl,
                    photo.getCaption(), photo.getTag(), photo.getUploadedBy(), photo.getUploadedAt()
            );
        }
    }

    record ExpenseSummaryResponse(Long id, LocalDate date, String category, String vendor, Long amount) {
        static ExpenseSummaryResponse of(Expense expense) {
            return new ExpenseSummaryResponse(expense.getId(), expense.getDate(), expense.getCategory(), expense.getVendor(), expense.getAmount());
        }
    }

    record LinkedSurveyResponse(Long id, String title, String status, Long createdBy, LocalDateTime createdAt) {
        static LinkedSurveyResponse of(Survey survey) {
            return new LinkedSurveyResponse(survey.getId(), survey.getTitle(), survey.getStatus().name(), survey.getCreatedBy(), survey.getCreatedAt());
        }
    }

    record LinkedTimetableResponse(Long id, String title, String status, Long createdBy, LocalDateTime createdAt) {
        static LinkedTimetableResponse of(Timetable timetable) {
            return new LinkedTimetableResponse(timetable.getId(), timetable.getTitle(), timetable.getStatus().name(), timetable.getCreatedBy(), timetable.getCreatedAt());
        }
    }
}
