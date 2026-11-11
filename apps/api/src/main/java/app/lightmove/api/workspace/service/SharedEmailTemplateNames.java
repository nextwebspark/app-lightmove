package app.lightmove.api.workspace.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The names of a workspace's shared email templates for the audit log, implemented in {@code outreach} so the dependency
 * stays one-way. A personal template, a deleted one or one of another workspace is absent: an admin never reads a name
 * its owner has not shared.
 */
public interface SharedEmailTemplateNames {

    Map<UUID, String> namesOf(UUID workspaceId, Collection<UUID> templateIds);
}
