package com.closecircle.api.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The person to invite. They don't need an account yet; they see the invitation once they register with this email. */
public record CreateInvitationRequest(@NotBlank @Email @Size(max = 320) String email) {
    public CreateInvitationRequest {
        email = email == null ? null : email.strip();
    }
}
