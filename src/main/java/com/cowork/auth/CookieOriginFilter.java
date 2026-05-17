package com.cowork.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

@RequiredArgsConstructor
@Component
public class CookieOriginFilter extends OncePerRequestFilter {

    private static final String DEFAULT_ALLOWED_ORIGINS =
            "http://localhost:5173,https://d3enhw6vmzgeun.cloudfront.net,https://cowork.kro.kr,https://3-35-27-121.nip.io";

    private final AuthCookieService authCookieService;

    @Value("${CORS_ALLOWED_ORIGIN_PATTERNS:" + DEFAULT_ALLOWED_ORIGINS + "}")
    private String corsAllowedOriginPatterns;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (requiresOriginCheck(request) && hasAuthCookie(request) && !hasAllowedBrowserOrigin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid request origin");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresOriginCheck(HttpServletRequest request) {
        String method = request.getMethod();
        return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method));
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        return StringUtils.hasText(authCookieService.resolveAccessToken(request))
                || StringUtils.hasText(authCookieService.resolveRefreshToken(request));
    }

    private boolean hasAllowedBrowserOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin)) {
            return isAllowedOrigin(origin);
        }

        String referer = request.getHeader("Referer");
        if (StringUtils.hasText(referer)) {
            String refererOrigin = resolveOrigin(referer);
            return refererOrigin == null || isAllowedOrigin(refererOrigin);
        }

        return true;
    }

    private boolean isAllowedOrigin(String origin) {
        return Arrays.stream(corsAllowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .anyMatch(pattern -> PatternMatchUtils.simpleMatch(pattern, origin));
    }

    private String resolveOrigin(String url) {
        try {
            URI uri = URI.create(url);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port == -1 ? "" : ":" + port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
