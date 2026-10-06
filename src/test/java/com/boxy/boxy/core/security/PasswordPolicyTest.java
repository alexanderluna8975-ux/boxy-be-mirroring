package com.boxy.boxy.core.security;

import com.boxy.boxy.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void rejectsAPasswordEqualToTheUsername() {
        assertThatThrownBy(() -> PasswordPolicy.rejectIfMatchesUsername("carlos", "carlos"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsCaseInsensitiveMatch() {
        assertThatThrownBy(() -> PasswordPolicy.rejectIfMatchesUsername("CARLOS", "carlos"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsAPasswordDifferentFromTheUsername() {
        assertThatCode(() -> PasswordPolicy.rejectIfMatchesUsername("a-real-password-123", "carlos"))
                .doesNotThrowAnyException();
    }
}
