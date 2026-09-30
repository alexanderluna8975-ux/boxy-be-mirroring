package com.boxy.boxy.core.security;

import com.boxy.boxy.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class RecaptchaServiceTest {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private record Fixture(RecaptchaService service, MockRestServiceServer server) {}

    private static Fixture build(boolean enabled, String secret, double minScore) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RecaptchaService service = new RecaptchaService(builder, enabled, secret, minScore, VERIFY_URL);
        return new Fixture(service, server);
    }

    @Test
    void constructorRejectsAMissingSecretWhenEnabled() {
        assertThatThrownBy(() -> build(true, "", 0.5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNothingWhenDisabledRegardlessOfToken() {
        Fixture fixture = build(false, "", 0.5);

        assertThatCode(() -> fixture.service().verify(null, "127.0.0.1", "login")).doesNotThrowAnyException();
        fixture.server().verify(); // no HTTP call made at all
    }

    @Test
    void rejectsAMissingTokenWithoutCallingGoogle() {
        Fixture fixture = build(true, "secret", 0.5);

        assertThatThrownBy(() -> fixture.service().verify("", "127.0.0.1", "login"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "RECAPTCHA_FAILED");
        fixture.server().verify();
    }

    @Test
    void acceptsAHighScoreMatchingAction() {
        Fixture fixture = build(true, "secret", 0.5);
        fixture.server().expect(method(POST)).andRespond(withSuccess(
                "{\"success\":true,\"score\":0.9,\"action\":\"login\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> fixture.service().verify("good-token", "127.0.0.1", "login"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAScoreBelowTheThreshold() {
        Fixture fixture = build(true, "secret", 0.5);
        fixture.server().expect(method(POST)).andRespond(withSuccess(
                "{\"success\":true,\"score\":0.2,\"action\":\"login\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.service().verify("low-score-token", "127.0.0.1", "login"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsAMismatchedAction() {
        Fixture fixture = build(true, "secret", 0.5);
        fixture.server().expect(method(POST)).andRespond(withSuccess(
                "{\"success\":true,\"score\":0.9,\"action\":\"some_other_form\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.service().verify("token-for-another-action", "127.0.0.1", "login"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsWhenGoogleReportsFailure() {
        Fixture fixture = build(true, "secret", 0.5);
        fixture.server().expect(method(POST)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.service().verify("expired-token", "127.0.0.1", "login"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsOnANetworkOrServerError() {
        Fixture fixture = build(true, "secret", 0.5);
        fixture.server().expect(method(POST)).andRespond(withServerError());

        assertThatThrownBy(() -> fixture.service().verify("token", "127.0.0.1", "login"))
                .isInstanceOf(BusinessException.class);
    }
}
