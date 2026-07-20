package com.findguni.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousDeviceServiceTest {

    private final AnonymousDeviceService devices = new AnonymousDeviceService(false);

    @Test
    void issuesOpaqueHttpOnlyCookieAndReusesItOnNextRequest() {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        String issued = devices.ensureToken(firstRequest, firstResponse);
        String setCookie = firstResponse.getHeader("Set-Cookie");

        assertThat(issued).hasSizeGreaterThanOrEqualTo(32);
        assertThat(setCookie)
                .contains(AnonymousDeviceService.COOKIE_NAME + "=" + issued)
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .doesNotContain("Secure");
        assertThat(devices.hash(issued)).matches("[0-9a-f]{64}");

        MockHttpServletRequest resumedRequest = new MockHttpServletRequest();
        resumedRequest.setCookies(new Cookie(AnonymousDeviceService.COOKIE_NAME, issued));
        MockHttpServletResponse resumedResponse = new MockHttpServletResponse();

        assertThat(devices.ensureToken(resumedRequest, resumedResponse)).isEqualTo(issued);
        assertThat(resumedResponse.getHeader("Set-Cookie")).isNull();
    }
}
