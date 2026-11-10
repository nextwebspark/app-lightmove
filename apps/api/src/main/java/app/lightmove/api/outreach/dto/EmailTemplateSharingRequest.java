package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotNull;

/** Share with workspace switched on or off, at the {@code version} the template was read at. */
public record EmailTemplateSharingRequest(@NotNull Boolean shared, @NotNull Long version) {}
