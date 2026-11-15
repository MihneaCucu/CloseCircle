package com.closecircle.api.sharing;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/me/sharing")
public class SharingController {
    private final SharingService sharing;

    public SharingController(SharingService sharing) {
        this.sharing = sharing;
    }

    @GetMapping
    public ResponseEntity<SharingSettings> get(@PathVariable UUID groupId, @AuthenticationPrincipal Jwt jwt) {
        return ok(sharing.get(groupId, userId(jwt)));
    }

    @PutMapping(consumes = "application/json")
    public ResponseEntity<SharingSettings> update(@PathVariable UUID groupId, @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode body) {
        UUID userId = userId(jwt);

        SharingSettings updated = sharing.update(groupId, userId, SharingUpdateRequest.parse(body));
        return ok(updated);
    }

    static UUID userId(Jwt jwt) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            if (!userId.toString().equalsIgnoreCase(jwt.getSubject())) {
                throw new IllegalArgumentException();
            }
            return userId;
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw SharingUpdateRequest.failure(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid token subject");
        }
    }

    private static ResponseEntity<SharingSettings> ok(SharingSettings settings) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        var builder = ResponseEntity.status(ex.getStatusCode()).cacheControl(CacheControl.noStore());
        if (ex.getStatusCode().value() == 401) builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return builder.body(ex.getBody());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
        return problem(SharingUpdateRequest.invalid());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> invalidParameter(MethodArgumentTypeMismatchException ex) {
        return problem(SharingUpdateRequest.failure(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid path parameter"));
    }
}
