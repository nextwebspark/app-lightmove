package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import java.util.UUID;

/** A person objected, or asked for a copy or a deletion, from their privacy page (#923). Ids only. */
public record PersonPrivacyRequested(UUID workspaceId, UUID personId, PrivacyRequestKind kind) {}
