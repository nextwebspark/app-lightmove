package app.lightmove.api.core.security.model;

import java.time.ZoneId;
import java.util.UUID;

/** A person moved their own clock in Settings → Profile; whatever keeps a copy of it follows in the same transaction. */
public record TimezoneChanged(UUID userId, ZoneId zone) {}
