package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The Candidates selection a privacy notice goes to. */
public record BulkPrivacyNoticeRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> personIds) {}
