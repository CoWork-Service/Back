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

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserRepository userRepository;

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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(EventDetailResponse.of(eventService.getEventDetail(id))));
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

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

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @PathVariable Long id,
            @PathVariable Long photoId) {
        eventService.deletePhoto(id, photoId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

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
