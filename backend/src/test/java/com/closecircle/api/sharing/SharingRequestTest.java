package com.closecircle.api.sharing;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class SharingRequestTest {
    private final ObjectMapper json = new ObjectMapper();

    private SharingUpdateRequest parse(String body) throws Exception {
        return SharingUpdateRequest.parse(json.readTree(body));
    }

    private void assertInvalid(String body) {
        var error = assertThrows(ResponseStatusException.class, () -> parse(body), body);
        assertEquals(400, error.getStatusCode().value());
        assertEquals("INVALID_SHARING_REQUEST", error.getBody().getProperties().get("code"));
    }

    @Test
    void parsesEachCombinationOfFlags() throws Exception {
        assertEquals(new SharingUpdateRequest(true, true, null),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":true}"));
        assertEquals(new SharingUpdateRequest(false, true, null),
                parse("{\"shareLiveLocation\":true,\"sharingEnabled\":false}"));
        assertEquals(new SharingUpdateRequest(true, false, null),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":false}"));
        assertEquals(new SharingUpdateRequest(false, false, null),
                parse("{\"sharingEnabled\":false,\"shareLiveLocation\":false}"));
    }

    @Test
    void requiresBothFlagsAsJsonBooleans() {
        assertInvalid("{}");
        assertInvalid("[]");
        assertInvalid("null");
        assertInvalid("true");
        assertInvalid("{\"sharingEnabled\":true}");
        assertInvalid("{\"shareLiveLocation\":true}");
        assertInvalid("{\"sharingEnabled\":null,\"shareLiveLocation\":true}");
        assertInvalid("{\"sharingEnabled\":\"true\",\"shareLiveLocation\":true}");
        assertInvalid("{\"sharingEnabled\":1,\"shareLiveLocation\":true}");
        assertInvalid("{\"sharingEnabled\":true,\"shareLiveLocation\":{}}");
    }

    @Test
    void rejectsUnknownFieldsIncludingIdentityOverrides() {
        assertInvalid("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"userId\":\"x\"}");
        assertInvalid("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"groupId\":\"x\"}");
        assertInvalid("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"updatedAt\":\"x\"}");
        assertInvalid("{\"sharingEnabled\":true,\"shareliveLocation\":true}");
    }

    @Test
    void parsesAnOptionalDurationInWholeMinutes() throws Exception {
        assertEquals(new SharingUpdateRequest(true, true, 60),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"durationMinutes\":60}"));
        assertEquals(new SharingUpdateRequest(true, true, 1),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"durationMinutes\":1}"));
        assertEquals(new SharingUpdateRequest(true, true, SharingUpdateRequest.MAX_DURATION_MINUTES),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"durationMinutes\":10080}"));
        assertEquals(new SharingUpdateRequest(true, true, null),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"durationMinutes\":null}"));
    }

    @Test
    void rejectsInvalidDurations() {
        for (String duration : new String[] {"0", "-1", "10081", "1.5", "\"60\"", "true", "{}", "2147483648"}) {
            assertInvalid("{\"sharingEnabled\":true,\"shareLiveLocation\":true,\"durationMinutes\":" + duration + "}");
        }
        // A duration only makes sense when turning sharing on.
        assertInvalid("{\"sharingEnabled\":false,\"shareLiveLocation\":true,\"durationMinutes\":60}");
        assertThrows(ResponseStatusException.class, () -> new SharingUpdateRequest(false, false, 60));
    }

    @Test
    void notSharedDefaultHasNoTimestamp() {
        var group = UUID.randomUUID();
        var user = UUID.randomUUID();
        assertEquals(new SharingSettings(group, user, false, false, null, null), SharingSettings.notShared(group, user));
    }

    private static Jwt jwt(String subject) {
        var builder = Jwt.withTokenValue("token").header("alg", "HS256")
                .issuedAt(Instant.parse("2026-11-15T00:00:00Z"))
                .expiresAt(Instant.parse("2026-11-15T01:00:00Z"));
        if (subject != null) builder.subject(subject);
        else builder.claim("iss", "closecircle");
        return builder.build();
    }

    @Test
    void takesTheCallerOnlyFromACanonicalUuidSubject() {
        var user = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(user, SharingController.userId(jwt(user.toString())));
        for (String subject : new String[] {null, "not-a-uuid", "0-0-0-0-1", ""}) {
            var error = assertThrows(ResponseStatusException.class, () -> SharingController.userId(jwt(subject)));
            assertEquals(401, error.getStatusCode().value());
            assertEquals("INVALID_TOKEN", error.getBody().getProperties().get("code"));
        }
    }
}
