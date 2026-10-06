package com.boxy.boxy.core.security;

import com.boxy.boxy.core.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Verifies a reCAPTCHA v3 token against Google's {@code siteverify} endpoint before
 * {@code AuthService} ever touches the password — a rejected token never counts as a login
 * failure ({@code AuthService.registerLoginFailure}) and never costs a BCrypt comparison, so a
 * bot farming captcha failures can't lock out real accounts or burn server CPU.
 * <p>
 * Fails closed: a missing token, a network error, a timeout, or a response that doesn't meet
 * every check (success, matching action, score above the threshold) is rejected the same way —
 * an outage on Google's side must never silently let every login through unchecked.
 */
@Slf4j
@Component
public class RecaptchaService {

    private final RestClient restClient;
    private final boolean enabled;
    private final String secret;
    private final double minScore;

    /**
     * The injected {@code RestClient.Builder} arrives already timeout-limited (3s connect / 5s
     * read) by {@link com.boxy.boxy.core.config.RestClientConfig}'s {@code RestClientCustomizer} —
     * deliberately NOT applied here via {@code .requestFactory(...)}, because that would overwrite
     * whatever request factory the caller already configured on the builder (in
     * {@code RecaptchaServiceTest}, that's the one {@code MockRestServiceServer.bindTo(builder)}
     * installs to intercept calls; calling {@code .requestFactory()} again after that silently
     * discards the mock and makes the test hit the real Google endpoint instead).
     */
    public RecaptchaService(
            RestClient.Builder restClientBuilder,
            @Value("${app.recaptcha.enabled:true}") boolean enabled,
            @Value("${app.recaptcha.secret:}") String secret,
            @Value("${app.recaptcha.min-score:0.5}") double minScore,
            @Value("${app.recaptcha.verify-url}") String verifyUrl) {
        this.enabled = enabled;
        this.secret = secret;
        this.minScore = minScore;

        if (enabled && (secret == null || secret.isBlank())) {
            throw new IllegalStateException(
                    "app.recaptcha.secret (RECAPTCHA_SECRET) must be set when reCAPTCHA is enabled. "
                            + "Set RECAPTCHA_ENABLED=false for an environment with no secret yet (e.g. local dev).");
        }

        this.restClient = restClientBuilder.baseUrl(verifyUrl).build();
    }

    /**
     * @param token          the client-supplied {@code g-recaptcha-response} value.
     * @param remoteIp       the caller's IP — passed to Google for its own risk analysis, not
     *                       used for anything on this side.
     * @param expectedAction must match the {@code action} the frontend passed to
     *                       {@code grecaptcha.execute(siteKey, {action})} — rejects a token
     *                       minted for a different action (e.g. a form on another page) being
     *                       replayed here.
     * @throws BusinessException ("RECAPTCHA_FAILED", 400) if the token is missing or doesn't
     *                           pass every check. No-op when reCAPTCHA is disabled.
     */
    public void verify(String token, String remoteIp, String expectedAction) {
        if (!enabled) {
            return;
        }
        if (token == null || token.isBlank()) {
            log.warn("reCAPTCHA rejected: no token presented (expected action '{}')", expectedAction);
            throw rejected();
        }

        SiteVerifyResponse response;
        try {
            response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("secret", secret)
                            .queryParam("response", token)
                            .queryParam("remoteip", remoteIp)
                            .build())
                    .retrieve()
                    .body(SiteVerifyResponse.class);
        } catch (RestClientException e) {
            log.warn("reCAPTCHA verification call failed: {}", e.getMessage());
            throw rejected();
        }

        if (response == null || !response.success()
                || response.score() == null || response.score() < minScore
                || !expectedAction.equals(response.action())) {
            log.warn("reCAPTCHA rejected: success={} score={} action={} (expected '{}') errors={}",
                    response != null ? response.success() : null,
                    response != null ? response.score() : null,
                    response != null ? response.action() : null,
                    expectedAction,
                    response != null ? response.errorCodes() : null);
            throw rejected();
        }
    }

    private static BusinessException rejected() {
        // Deliberately generic — same principle as GlobalExceptionHandler's bad-credentials
        // message: don't give an automated caller any signal about which check it failed.
        return new BusinessException("RECAPTCHA_FAILED", "reCAPTCHA verification failed.", HttpStatus.BAD_REQUEST);
    }

    private record SiteVerifyResponse(
            boolean success,
            Double score,
            String action,
            @JsonProperty("challenge_ts") String challengeTs,
            String hostname,
            @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
