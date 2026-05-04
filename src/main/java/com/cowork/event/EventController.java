package com.cowork.event;

import com.cowork.budget.Expense;
import com.cowork.common.ApiResponse;
import com.cowork.cohort.Department;
import com.cowork.schedule.Timetable;
import com.cowork.survey.Survey;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
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

/**
 * 행사 관리 컨트롤러 (EventController)
 *
 * 역할:
 *   코호트의 행사(축제·발표회·워크숍 등)를 관리하는 API 를 제공한다.
 *   기본 경로: /api/events
 *
 * 주요 기능:
 *   - 행사 CRUD
 *   - 행사 사진 업로드·삭제
 *   - 행사 상세 조회 시 사진·지출·설문·일정조율표가 함께 반환됨
 *
 * 인증 필요: 모든 엔드포인트에 JWT Access Token 필요
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserRepository userRepository;

    /**
     * 행사 목록 조회
     *
     * 동작: cohortId 기준으로 행사 목록을 조회하며, 상태·분류로 필터링 가능.
     * 사용 시점: 행사 목록 화면 또는 캘린더에서 행사를 표시할 때.
     *
     * @param cohortId 필수. 조회할 코호트 ID
     * @param status   선택. 행사 상태 필터 (PLANNING / IN_PROGRESS / COMPLETED / CANCELLED)
     * @param category 선택. 분류 필터 (예: "축제")
     * @return 행사 목록 (요약 정보)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventSummaryResponse>>> getEvents(
            @RequestParam Long cohortId,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String category) {
        List<EventSummaryResponse> events = eventService.getEvents(cohortId, status, category).stream()
                .map(EventSummaryResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    /**
     * 행사 생성
     *
     * 동작: 새 행사를 생성한다. 생성자(createdBy)는 JWT 토큰에서 자동으로 설정된다.
     * 사용 시점: 새 행사를 기획할 때.
     *
     * @param request     행사 정보 (name, category, status, description, startDate, endDate,
     *                    location, leadDepartment, organizers, budget, coverColor, cohortId)
     * @param userDetails 현재 로그인 사용자 (생성자)
     * @return 생성된 행사 상세 정보
     */
    @PostMapping
    public ResponseEntity<ApiResponse<EventDetailResponse>> createEvent(
            @RequestBody EventRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        EventService.EventDetail detail = eventService.createEvent(
                request.getCohortId(),
                request.getName(),
                request.getCategory(),
                request.getStatus(),
                request.getDescription(),
                request.getStartDate(),
                request.getEndDate(),
                request.getLocation(),
                request.getLeadDepartment(),
                request.getOrganizers(),
                request.getBudget(),
                request.getCoverColor(),
                user.getId()
        );
        return ResponseEntity.ok(ApiResponse.ok(EventDetailResponse.of(detail)));
    }

    /**
     * 행사 상세 조회
     *
     * 동작: 행사 기본 정보 + 사진 + 지출 내역 + 설문 + 일정 조율표를 한 번에 반환한다.
     * 사용 시점: 행사 상세 화면에서 전체 정보를 한 번의 요청으로 불러올 때.
     *
     * @param id 행사 ID
     * @return 행사 + 사진 목록 + 지출 요약 + 설문 목록 + 타임테이블 목록
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(EventDetailResponse.of(eventService.getEventDetail(id))));
    }

    /**
     * 행사 정보 수정
     *
     * 동작: 행사의 모든 기본 정보를 수정한다 (상태 변경 포함).
     * 사용 시점: 행사 편집 폼에서 저장하거나 상태를 변경할 때.
     *
     * @param id      행사 ID
     * @param request 수정할 행사 정보
     * @return 수정된 행사 상세 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> updateEvent(
            @PathVariable Long id,
            @RequestBody EventRequest request) {
        EventService.EventDetail detail = eventService.updateEvent(
                id,
                request.getName(),
                request.getCategory(),
                request.getStatus(),
                request.getDescription(),
                request.getStartDate(),
                request.getEndDate(),
                request.getLocation(),
                request.getLeadDepartment(),
                request.getOrganizers(),
                request.getBudget(),
                request.getCoverColor()
        );
        return ResponseEntity.ok(ApiResponse.ok(EventDetailResponse.of(detail)));
    }

    /**
     * 행사 삭제
     *
     * 동작: 행사와 연관된 사진 파일 및 레코드를 삭제한다.
     * 사용 시점: 취소된 행사를 완전히 제거할 때.
     *
     * @param id 행사 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 행사 사진 업로드
     *
     * 동작: 행사에 사진을 추가한다. 파일을 스토리지에 저장하고 EventPhoto 레코드를 생성한다.
     * 사용 시점: 행사 진행 중 또는 후 사진을 등록할 때.
     *
     * Content-Type: multipart/form-data
     * @param id          행사 ID
     * @param photo       사진 파일
     * @param caption     선택. 사진 설명
     * @param tag         선택. 사진 태그 (예: "현장", "준비", "기타")
     * @param userDetails 현재 로그인 사용자 (업로드자)
     * @return 생성된 사진 정보 (id, storagePath, photoUrl, caption, tag 등)
     */
    @PostMapping("/{id}/photos")
    public ResponseEntity<ApiResponse<EventPhotoResponse>> addPhoto(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) String tag,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        EventPhoto item = eventService.addPhoto(id, photo, caption, tag, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(EventPhotoResponse.of(item)));
    }

    /**
     * 행사 사진 삭제
     *
     * 동작: 사진 파일을 스토리지에서 삭제하고 EventPhoto 레코드를 제거한다.
     * 사용 시점: 잘못 업로드된 사진이나 부적절한 사진을 제거할 때.
     *
     * @param id      행사 ID
     * @param photoId 삭제할 사진 ID
     */
    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @PathVariable Long id,
            @PathVariable Long photoId) {
        eventService.deletePhoto(id, photoId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** JWT 의 username(= userId) 로 User 엔티티를 로드하는 내부 헬퍼 */
    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** POST/PUT 행사 요청 바디 */
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
        private Department leadDepartment;
        private List<String> organizers;
        private Long budget;
        private String coverColor;
    }

    /** 행사 요약 응답 DTO (목록용) */
    record EventSummaryResponse(Long id, Long cohortId, String name, String category, String status,
                                String description, LocalDate startDate, LocalDate endDate, String location,
                                String leadDepartment, List<String> organizers, Long budget, String coverColor,
                                Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static EventSummaryResponse of(CoworkEvent event) {
            return new EventSummaryResponse(
                    event.getId(),
                    event.getCohortId(),
                    event.getName(),
                    event.getCategory(),
                    event.getStatus().name(),
                    event.getDescription(),
                    event.getStartDate(),
                    event.getEndDate(),
                    event.getLocation(),
                    event.getLeadDepartment() != null ? event.getLeadDepartment().name() : null,
                    event.getOrganizers(),
                    event.getBudget(),
                    event.getCoverColor(),
                    event.getCreatedBy(),
                    event.getCreatedAt(),
                    event.getUpdatedAt()
            );
        }
    }

    /** 행사 상세 응답 DTO (사진·지출·설문·타임테이블 포함) */
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

    /** 사진 응답 DTO — photoUrl 은 "/uploads/" + storagePath 로 구성 */
    record EventPhotoResponse(Long id, String storagePath, String photoUrl, String caption, String tag,
                              Long uploadedBy, LocalDateTime uploadedAt) {
        static EventPhotoResponse of(EventPhoto photo) {
            return new EventPhotoResponse(
                    photo.getId(),
                    photo.getStoragePath(),
                    "/uploads/" + photo.getStoragePath(),
                    photo.getCaption(),
                    photo.getTag(),
                    photo.getUploadedBy(),
                    photo.getUploadedAt()
            );
        }
    }

    /** 행사에 연결된 지출 요약 DTO */
    record ExpenseSummaryResponse(Long id, LocalDate date, String category, String vendor, Long amount) {
        static ExpenseSummaryResponse of(Expense expense) {
            return new ExpenseSummaryResponse(expense.getId(), expense.getDate(), expense.getCategory(), expense.getVendor(), expense.getAmount());
        }
    }

    /** 행사에 연결된 설문 요약 DTO */
    record LinkedSurveyResponse(Long id, String title, String status, Long createdBy, LocalDateTime createdAt) {
        static LinkedSurveyResponse of(Survey survey) {
            return new LinkedSurveyResponse(survey.getId(), survey.getTitle(), survey.getStatus().name(), survey.getCreatedBy(), survey.getCreatedAt());
        }
    }

    /** 행사에 연결된 타임테이블 요약 DTO */
    record LinkedTimetableResponse(Long id, String title, String status, Long createdBy, LocalDateTime createdAt) {
        static LinkedTimetableResponse of(Timetable timetable) {
            return new LinkedTimetableResponse(timetable.getId(), timetable.getTitle(), timetable.getStatus().name(), timetable.getCreatedBy(), timetable.getCreatedAt());
        }
    }
}
