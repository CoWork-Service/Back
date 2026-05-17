package com.cowork.consent;

import com.cowork.common.ApiResponse;
import com.cowork.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class PolicyConsentFilter extends OncePerRequestFilter {

    private final PolicyConsentService policyConsentService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = normalizedPath(request);
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method) || !path.startsWith("/api/")) {
            return true;
        }

        if (path.equals("/api/auth/me") ||
                path.equals("/api/auth/consent") ||
                matches("/api/auth/consent/**", path) ||
                path.equals("/api/auth/login") ||
                path.equals("/api/auth/register") ||
                path.equals("/api/auth/refresh") ||
                path.equals("/api/auth/logout") ||
                matches("/api/auth/sso/**", path)) {
            return true;
        }

        if ("GET".equalsIgnoreCase(method) && matches("/api/mobile/sessions/*", path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && matches("/api/mobile/sessions/*/ocr", path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && matches("/api/mobile/sessions/*/upload", path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && matches("/api/mobile/sessions/*/expense", path)) {
            return true;
        }

        if ("GET".equalsIgnoreCase(method) && matches("/api/surveys/*", path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && matches("/api/surveys/*/respond", path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && matches("/api/timetables/*", path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && matches("/api/timetables/*/respond", path)) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof UserDetails userDetails) {
            Long userId = Long.parseLong(userDetails.getUsername());
            if (policyConsentService.isConsentRequired(userId)) {
                writeConsentRequired(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeConsentRequired(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.POLICY_CONSENT_REQUIRED.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.POLICY_CONSENT_REQUIRED));
    }

    private boolean matches(String pattern, String path) {
        return pathMatcher.match(pattern, path);
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
