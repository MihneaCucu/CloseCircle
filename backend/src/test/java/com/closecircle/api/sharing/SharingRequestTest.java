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
        assertEquals(new SharingUpdateRequest(true, true),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":true}"));
        assertEquals(new SharingUpdateRequest(false, true),
                parse("{\"shareLiveLocation\":true,\"sharingEnabled\":false}"));
        assertEquals(new SharingUpdateRequest(true, false),
                parse("{\"sharingEnabled\":true,\"shareLiveLocation\":false}"));
        assertEquals(new SharingUpdateRequest(false, false),
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
    void notSharedDefaultHasNoTimestamp() {
        var group = UUID.randomUUID();
        var user = UUID.randomUUID();
        assertEquals(new SharingSettings(group, user, false, false, null), SharingSettings.notShared(group, user));
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
