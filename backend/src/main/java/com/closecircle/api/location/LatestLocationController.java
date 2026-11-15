package com.closecircle.api.location;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/locations")
public class LatestLocationController {
    private final LatestLocationService locations;

    public LatestLocationController(LatestLocationService locations) {
        this.locations = locations;
    }

    @GetMapping("/latest")
    public ResponseEntity<LatestLocationService.Page> latest(@PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
            if (!userId.toString().equalsIgnoreCase(jwt.getSubject())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(locations.latest(groupId, userId, limit, cursor));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        ProblemDetail body = ex.getBody();
        body.setProperty("code", switch (ex.getStatusCode().value()) {
            case 400 -> "INVALID_CURSOR";
            case 401 -> "INVALID_TOKEN";
            case 404 -> "GROUP_NOT_FOUND";
            default -> "REQUEST_FAILED";
        });
        var response = ResponseEntity.status(ex.getStatusCode()).cacheControl(CacheControl.noStore());
        if (ex.getStatusCode().value() == 401) response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> invalidParameter(MethodArgumentTypeMismatchException ex) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid path or query parameter");
        body.setProperty("code", "INVALID_PARAMETER");
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(body);
    }
}
