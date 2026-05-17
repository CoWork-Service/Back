package com.cowork.consent;

import com.cowork.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/consent")
@RequiredArgsConstructor
public class PolicyConsentController {

    private final PolicyConsentService policyConsentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PolicyConsentService.ConsentStatus>> getConsentStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(policyConsentService.getStatus(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyConsentService.ConsentStatus>> agree(
            @RequestBody ConsentRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest servletRequest) {
        Long userId = Long.parseLong(userDetails.getUsername());
        PolicyConsentService.ConsentStatus status = policyConsentService.agree(
                userId,
                request.isTermsAgreed(),
                request.isPrivacyAgreed(),
                request.getTermsVersion(),
                request.getPrivacyVersion(),
                resolveClientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp : request.getRemoteAddr();
    }

    @Getter
    static class ConsentRequest {
        private boolean termsAgreed;
        private boolean privacyAgreed;
        private String termsVersion;
        private String privacyVersion;
    }
}
