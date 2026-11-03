package com.closecircle.api.sharing;

import jakarta.validation.constraints.NotNull;

public record UpdateSharingRequest(@NotNull Boolean sharingEnabled, @NotNull Boolean shareLiveLocation) {}
