package com.closecircle.api.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(@NotBlank @Size(max = 100) String name) {
    /** Trims surrounding whitespace before validation, so "   " is rejected as blank. */
    public CreateGroupRequest {
        name = name == null ? null : name.strip();
    }
}
