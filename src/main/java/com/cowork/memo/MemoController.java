package com.cowork.memo;

import com.cowork.cohort.Department;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/memos")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemoResponse>>> getMemos(
            @RequestParam Long cohortId,
            @RequestParam(required = false) MemoStatus status,
            @RequestParam(required = false) MemoPriority priority,
            @RequestParam(required = false) Department department) {
        List<MemoResponse> list = memoService.getMemos(cohortId, status, priority, department)
                .stream().map(MemoResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemoResponse>> createMemo(
            @RequestBody MemoRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        Memo memo = memoService.createMemo(req.getCohortId(), req.getTitle(), req.getContent(),
                req.getDepartment(), req.getPriority(), req.getStatus(), req.getDueDate(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoResponse>> updateMemo(
            @PathVariable Long id,
            @RequestBody MemoRequest req) {
        Memo memo = memoService.updateMemo(id, req.getTitle(), req.getContent(),
                req.getDepartment(), req.getPriority(), req.getStatus(), req.getDueDate());
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<MemoResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Memo memo = memoService.updateStatus(id, MemoStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<ApiResponse<MemoResponse>> updatePriority(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Memo memo = memoService.updatePriority(id, MemoPriority.valueOf(body.get("priority")));
        return ResponseEntity.ok(ApiResponse.ok(MemoResponse.of(memo)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMemo(@PathVariable Long id) {
        memoService.deleteMemo(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Getter
    static class MemoRequest {
        private Long cohortId;
        private String title;
        private String content;
        private Department department;
        private MemoPriority priority;
        private MemoStatus status;
        private LocalDate dueDate;
    }

    record MemoResponse(Long id, Long cohortId, String title, String content, String department,
                        String priority, String status, LocalDate dueDate, String author,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        static MemoResponse of(Memo m) {
            return new MemoResponse(m.getId(), m.getCohortId(), m.getTitle(), m.getContent(),
                    m.getDepartment() != null ? m.getDepartment().name() : null,
                    m.getPriority().name(), m.getStatus().name(), m.getDueDate(), m.getAuthor(),
                    m.getCreatedAt(), m.getUpdatedAt());
        }
    }
}
