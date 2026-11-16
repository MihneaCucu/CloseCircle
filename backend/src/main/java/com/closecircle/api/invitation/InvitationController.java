package com.closecircle.api.invitation;

import java.util.UUID;

import com.closecircle.api.pagination.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller is always taken from the token ("sub"); their role is checked against the database. */
@RestController
@RequestMapping("/api/v1")
public class InvitationController {
    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Owner or admin invites someone by email. */
    @PostMapping("/groups/{groupId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(@PathVariable UUID groupId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.create(groupId, currentUserId(jwt), request);
    }

    /** Owner or admin sees the group's invitations and their status. */
    @GetMapping("/groups/{groupId}/invitations")
    public PageResponse<InvitationResponse> listForGroup(@PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return invitationService.listForGroup(groupId, currentUserId(jwt), limit, cursor);
    }

    /** The invitee sees the invitations waiting for them. */
    @GetMapping("/invitations")
    public PageResponse<ReceivedInvitationResponse> listReceived(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return invitationService.listReceived(currentUserId(jwt), limit, cursor);
    }

    private static UUID currentUserId(Jwt jwt) {
        // SecurityConfig has already verified that "sub" is a UUID.
        return UUID.fromString(jwt.getSubject());
    }
}
