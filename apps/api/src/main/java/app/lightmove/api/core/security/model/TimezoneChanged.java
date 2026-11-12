package app.lightmove.api.core.security.model;

import java.util.UUID;

/** A person's settled zone moved — a pin, or their device while Automatic; whatever copies it reads it again. */
public record TimezoneChanged(UUID userId) {}
