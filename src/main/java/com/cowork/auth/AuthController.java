package com.cowork.auth;

import com.cowork.auth.dto.LoginRequest;
import com.cowork.auth.dto.RegisterRequest;
import com.cowork.auth.dto.TokenResponse;
import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(req.getRefreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(Long.parseLong(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        User user = userRepository.findById(userId).orElseThrow();
        return ResponseEntity.ok(ApiResponse.ok(new MeResponse(user.getId(), user.getName(), user.getEmail(),
                user.getOrganization().getId(), user.getOrganization().getName())));
    }

    @lombok.Getter
    static class RefreshRequest {
        private String refreshToken;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    static class MeResponse {
        private Long userId;
        private String name;
        private String email;
        private Long organizationId;
        private String organizationName;
    }
}
