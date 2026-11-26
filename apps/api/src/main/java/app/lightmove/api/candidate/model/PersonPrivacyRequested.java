package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import java.util.UUID;

/**
 * A person objected, or asked for a copy or a deletion, from their privacy page (#923); or a colleague recorded their
 * objection (B16), who is then {@code actorUserId} and is not told of their own act. Ids only.
 */
public record PersonPrivacyRequested(UUID workspaceId, UUID personId, PrivacyRequestKind kind, UUID actorUserId) {

    public PersonPrivacyRequested(UUID workspaceId, UUID personId, PrivacyRequestKind kind) {
        this(workspaceId, personId, kind, null);
    }
}
