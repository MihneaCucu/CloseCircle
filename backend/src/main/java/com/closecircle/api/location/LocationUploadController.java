package com.closecircle.api.location;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/locations")
public class LocationUploadController {
    private final LocationUploadService locations;

    public LocationUploadController(LocationUploadService locations) {
        this.locations = locations;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<LatestLocationService.Location> upload(@PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
            if (!userId.toString().equalsIgnoreCase(jwt.getSubject())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw LocationUploadRequest.failure(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid token subject");
        }
        UUID uploadKey = null;
        if (key != null) {
            try {
                uploadKey = UUID.fromString(key);
                if (!uploadKey.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw LocationUploadRequest.failure(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_KEY", "Invalid upload key");
            }
        }
        var result = locations.upload(groupId, userId, LocationUploadRequest.parse(body), uploadKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result.location());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        var builder = ResponseEntity.status(ex.getStatusCode()).cacheControl(CacheControl.noStore());
        if (ex.getStatusCode().value() == 401) builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return builder.body(ex.getBody());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalid(Exception ex) {
        return problem(LocationUploadRequest.invalid());
    }
}
