package com.cowork.auth;

import com.cowork.auth.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final JwtUtil jwtUtil;

    @Value("${auth.cookie.access-name:cowork_access_token}")
    private String accessCookieName;

    @Value("${auth.cookie.refresh-name:cowork_refresh_token}")
    private String refreshCookieName;

    @Value("${auth.cookie.secure:false}")
    private boolean secure;

    @Value("${auth.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${auth.cookie.domain:}")
    private String domain;

    public void addAuthCookies(HttpServletResponse response, TokenResponse tokenResponse) {
        addCookie(response, accessCookieName, tokenResponse.getAccessToken(), jwtUtil.getAccessTokenExpiry());
        addCookie(response, refreshCookieName, tokenResponse.getRefreshToken(), jwtUtil.getRefreshTokenExpiry());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, accessCookieName, "", 0);
        addCookie(response, refreshCookieName, "", 0);
    }

    public String resolveAccessToken(HttpServletRequest request) {
        return resolveCookie(request, accessCookieName);
    }

    public String resolveRefreshToken(HttpServletRequest request) {
        return resolveCookie(request, refreshCookieName);
    }

    private String resolveCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds));

        if (StringUtils.hasText(sameSite)) {
            builder.sameSite(sameSite);
        }
        if (StringUtils.hasText(domain)) {
            builder.domain(domain);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
