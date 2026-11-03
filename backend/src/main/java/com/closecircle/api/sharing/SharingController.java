package com.closecircle.api.sharing;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/me/sharing")
public class SharingController {
    private final SharingService sharing;

    public SharingController(SharingService sharing) {
        this.sharing = sharing;
    }

    @GetMapping
    public SharingPreferences get(@PathVariable UUID groupId, @AuthenticationPrincipal Jwt jwt) {
        return sharing.getPreferences(groupId, userId(jwt));
    }

    @PutMapping
    public SharingPreferences update(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSharingRequest request) {
        return sharing.updatePreferences(
                groupId, userId(jwt), request.sharingEnabled(), request.shareLiveLocation());
    }

    private static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
        }
    }
}
