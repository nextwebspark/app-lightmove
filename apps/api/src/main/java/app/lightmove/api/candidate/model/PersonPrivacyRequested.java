package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A person objected, or asked for a copy or a deletion, from their privacy page (#923). Ids only. */
@NamedInterface(name = "api", propagate = false)
public record PersonPrivacyRequested(UUID workspaceId, UUID personId, PrivacyRequestKind kind) {}
