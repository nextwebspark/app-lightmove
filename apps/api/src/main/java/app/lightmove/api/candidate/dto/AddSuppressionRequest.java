package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.WorkspaceSuppression;
import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * An admin's own entry: a LinkedIn profile, an email address or both, kept as hashes only. {@code reason} is
 * {@code manual} (the default) or {@code objected}; an erasure writes its own entries.
 */
public record AddSuppressionRequest(
        @Size(max = 500) String linkedinUrl,
        @Email @Size(max = 320) @JsonDeserialize(converter = EmailAddressNormaliser.class) String email,
        String reason,
        @Size(max = WorkspaceSuppression.MAX_NOTE) String note
) {}
