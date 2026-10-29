package app.lightmove.api.core.security.model;

import java.util.UUID;

/**
 * A person moved their own clock in Settings → Profile, or went back to the workspace's; whatever keeps a copy of it
 * reads it again in the same transaction.
 */
public record TimezoneChanged(UUID userId) {}
