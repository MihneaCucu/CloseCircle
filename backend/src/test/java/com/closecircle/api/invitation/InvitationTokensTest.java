package com.closecircle.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvitationTokensTest {

    @Test
    void tokensAreRandomAndUrlSafe() {
        String first = InvitationTokens.newToken();
        String second = InvitationTokens.newToken();

        assertThat(first).isNotEqualTo(second).matches("[A-Za-z0-9_-]{43}"); // 32 bytes in Base64 without padding
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        // Known SHA-256 of "abc".
        assertThat(InvitationTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        String token = InvitationTokens.newToken();
        assertThat(InvitationTokens.hash(token)).isEqualTo(InvitationTokens.hash(token)).doesNotContain(token);
    }
}
