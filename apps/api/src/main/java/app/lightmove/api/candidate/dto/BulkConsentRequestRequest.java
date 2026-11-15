package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The Candidates selection to ask, and the purposes, as {@code ConsentPurpose} wire tokens. */
public record BulkConsentRequestRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> personIds,
                                        @NotEmpty @Size(max = 3) List<@NotNull String> purposes) {}
