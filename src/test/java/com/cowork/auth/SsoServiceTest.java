package com.cowork.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SsoServiceTest {

    @Test
    void extractsPlainEmailFromSetCookie() {
        String email = SsoService.extractEmailFromCookies(List.of(
                "JSESSIONID=abc; Path=/; HttpOnly",
                "mail=student@soongsil.ac.kr; Path=/"
        ));

        assertThat(email).isEqualTo("student@soongsil.ac.kr");
    }

    @Test
    void extractsUrlEncodedEmailFromSetCookie() {
        String email = SsoService.extractEmailFromCookies(List.of(
                "profile=%7B%22email%22%3A%22Student%2540soongsil.ac.kr%22%7D; Path=/"
        ));

        assertThat(email).isEqualTo("student@soongsil.ac.kr");
    }
}
