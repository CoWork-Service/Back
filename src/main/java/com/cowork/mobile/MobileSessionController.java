package com.cowork.mobile;

import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/sessions")
@RequiredArgsConstructor
public class MobileSessionController {

    private final MobileSessionService mobileSessionService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<MobileSessionResponse>> createSession(
            @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        MobileSession session = mobileSessionService.createSession(request.getCohortId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(MobileSessionResponse.of(session)));
    }

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<MobileSessionStatusResponse>> getSession(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(MobileSessionStatusResponse.of(mobileSessionService.getSession(token))));
    }

    @PostMapping("/{token}/upload")
    public ResponseEntity<ApiResponse<MobileUploadResponse>> upload(
            @PathVariable String token,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(required = false) String extraData) {
        MobileSession session = mobileSessionService.upload(token, photo, extraData);
        return ResponseEntity.ok(ApiResponse.ok(MobileUploadResponse.of(session)));
    }

    @GetMapping("/{token}/result")
    public ResponseEntity<ApiResponse<MobileUploadResponse>> getResult(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(MobileUploadResponse.of(mobileSessionService.getResult(token))));
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String token) {
        mobileSessionService.deleteSession(token);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    @Getter
    static class CreateSessionRequest {
        private Long cohortId;
    }

    record MobileSessionResponse(String sessionToken, String qrUrl, LocalDateTime expiresAt) {
        static MobileSessionResponse of(MobileSession session) {
            return new MobileSessionResponse(
                    session.getSessionToken(),
                    "/m/expense/" + session.getSessionToken(),
                    session.getExpiresAt()
            );
        }
    }

    record MobileSessionStatusResponse(String sessionToken, boolean used, boolean expired, LocalDateTime expiresAt) {
        static MobileSessionStatusResponse of(MobileSession session) {
            return new MobileSessionStatusResponse(
                    session.getSessionToken(),
                    session.isUsed(),
                    session.isExpired(),
                    session.getExpiresAt()
            );
        }
    }

    record MobileUploadResponse(boolean used, String photoPath, String photoUrl,
                                Map<String, Object> extraData, LocalDateTime expiresAt) {
        static MobileUploadResponse of(MobileSession session) {
            return new MobileUploadResponse(
                    session.isUsed(),
                    session.getPhotoPath(),
                    session.getPhotoPath() != null ? "/uploads/" + session.getPhotoPath() : null,
                    session.getExtraData(),
                    session.getExpiresAt()
            );
        }
    }
}
