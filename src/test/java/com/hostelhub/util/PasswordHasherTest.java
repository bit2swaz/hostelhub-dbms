package com.hostelhub.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void hashIsPbkdf2FormatAndVerifies() {
        String h = PasswordHasher.hash("Hostel@123");
        assertThat(h).matches("pbkdf2\\$600000\\$[A-Za-z0-9+/=]{24}\\$[A-Za-z0-9+/=]{44}").doesNotContain("Hostel@123");
        assertThat(PasswordHasher.verify("Hostel@123", h)).isTrue();
        assertThat(PasswordHasher.verify("hostel@123", h)).isFalse();
    }

    @Test
    void samePasswordGetsDifferentSalts() {
        assertThat(PasswordHasher.hash("Hostel@123")).isNotEqualTo(PasswordHasher.hash("Hostel@123"));
    }

    @Test
    void malformedOrNullNeverVerifies() {
        assertThat(PasswordHasher.verify("x", "plain-text")).isFalse();
        assertThat(PasswordHasher.verify("x", "pbkdf2$abc$def$ghi")).isFalse();
        assertThat(PasswordHasher.verify(null, PasswordHasher.hash("x"))).isFalse();
        assertThat(PasswordHasher.verify("x", null)).isFalse();
    }
}
