package com.boxy.boxy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// JwtTokenProvider refuses to start without a >= 64-byte app.jwt.secret and application.yml
// intentionally has no default (see JWT_SECRET) — this test-only value satisfies that check
// without weakening the production default. Same reasoning for app.recaptcha.enabled=false:
// RecaptchaService fails closed and refuses to start when enabled with no secret configured
// (see RECAPTCHA_SECRET), and there's no real secret to give it here.
@SpringBootTest
@TestPropertySource(properties = {
		"app.jwt.secret=test-only-jwt-signing-secret-do-not-use-in-any-real-environment-1234567890",
		"app.recaptcha.enabled=false"
})
class BoxyApplicationTests {

	@Test
	void contextLoads() {
	}

}
