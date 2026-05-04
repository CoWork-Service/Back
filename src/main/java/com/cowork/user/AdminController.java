package com.cowork.user;

import com.cowork.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users/pending")
    public ResponseEntity<ApiResponse<List<PendingUserResponse>>> getPendingUsers() {
        List<PendingUserResponse> users = adminService.getPendingUsers().stream()
                .map(PendingUserResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @PatchMapping("/users/{userId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveUser(@PathVariable Long userId) {
        adminService.approveUser(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/users/{userId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectUser(@PathVariable Long userId) {
        adminService.rejectUser(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    record PendingUserResponse(Long userId, String name, String email, String studentId,
                               String department, String joinStatus) {
        static PendingUserResponse of(User user) {
            return new PendingUserResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getStudentId(),
                    user.getOrganization() != null ? user.getOrganization().getDepartment() : null,
                    user.getJoinStatus().name()
            );
        }
    }
}