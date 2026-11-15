package com.closecircle.api.group;

import java.util.UUID;

import com.closecircle.api.pagination.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller is always taken from the token ("sub"), never from the request body. */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(currentUserId(jwt), request);
    }

    @GetMapping
    public PageResponse<GroupResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return groupService.listForUser(currentUserId(jwt), limit, cursor);
    }

    private static UUID currentUserId(Jwt jwt) {
        // SecurityConfig has already verified that "sub" is a UUID.
        return UUID.fromString(jwt.getSubject());
    }
}
