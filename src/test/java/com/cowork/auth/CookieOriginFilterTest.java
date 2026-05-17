package com.cowork.auth;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookieOriginFilterTest {

    @Mock
    private AuthCookieService authCookieService;

    private CookieOriginFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CookieOriginFilter(authCookieService);
        ReflectionTestUtils.setField(filter, "corsAllowedOriginPatterns", "https://cowork.kro.kr");
    }

    @Test
    void rejectsStateChangingCookieRequestFromUnknownOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/memos");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authCookieService.resolveAccessToken(request)).thenReturn("access-token");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsStateChangingCookieRequestFromAllowedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/memos");
        request.addHeader("Origin", "https://cowork.kro.kr");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(authCookieService.resolveAccessToken(request)).thenReturn("access-token");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void allowsPublicStateChangingRequestWithoutAuthCookie() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mobile/sessions/token/expense");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
