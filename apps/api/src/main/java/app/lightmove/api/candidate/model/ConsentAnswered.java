package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * A person answered a consent request on their page. The first of {@code recipientUserIds} still on the staff hears of
 * it: whoever asked, then their owner, then whoever sent the link.
 */
@NamedInterface(name = "api", propagate = false)
public record ConsentAnswered(UUID workspaceId, UUID personId, List<UUID> recipientUserIds) {}
