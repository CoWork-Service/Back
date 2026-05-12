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

    @Test
    void ignoresDoorayEmailFromSetCookie() {
        String email = SsoService.extractEmailFromCookies(List.of(
                "mail=sos@soongsil.dooray.com; Path=/"
        ));

        assertThat(email).isNull();
    }

    @Test
    void resolvesParsedSoongsilEmailBeforeRequestEmail() {
        SsoService service = new SsoService(null, null, null, null, null, null, null, null);

        String email = service.resolveEmail(
                "sos@soongsil.dooray.com",
                "student@soongsil.ac.kr",
                "20231728"
        );

        assertThat(email).isEqualTo("student@soongsil.ac.kr");
    }

    @Test
    void fallsBackToStudentIdEmailWhenNoValidEmailExists() {
        SsoService service = new SsoService(null, null, null, null, null, null, null, null);

        String email = service.resolveEmail(null, null, "20231728");

        assertThat(email).isEqualTo("20231728@soongsil.ac.kr");
    }
}
