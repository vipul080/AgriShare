package com.vipul.agrishare.security;

import com.vipul.agrishare.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;

    private final UserPrincipal user = new UserPrincipal(User.builder().id(42L).role(User.Role.BOTH).build());

    @Test
    void withoutSecret_usesRandomKey_soTokensFromAnotherStartAreRejected() {
        JwtService first = new JwtService("", WEEK);
        JwtService second = new JwtService("", WEEK);
        String token = first.generateToken(user);

        assertThat(first.extractUserId(token)).isEqualTo(42L);
        assertThatThrownBy(() -> second.extractUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void configuredSecret_isStableAcrossRestarts() {
        String secret = "a-long-random-production-secret-value-1234";
        String token = new JwtService(secret, WEEK).generateToken(user);

        assertThat(new JwtService(secret, WEEK).isTokenValid(token, user)).isTrue();
    }

    @Test
    void shortSecret_refusesToStart() {
        assertThatThrownBy(() -> new JwtService("too-short", WEEK)).isInstanceOf(IllegalStateException.class);
    }
}
