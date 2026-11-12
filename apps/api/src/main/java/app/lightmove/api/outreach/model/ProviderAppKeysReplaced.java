package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.IntegrationProvider;
import java.util.UUID;

/**
 * A workspace kept its own app at a provider but saved new keys for it — a rotated secret, or a corrected tenant. Once
 * it commits, every Recall calendar made with the old keys is handed the new ones.
 */
public record ProviderAppKeysReplaced(UUID workspaceId, IntegrationProvider provider) {}
