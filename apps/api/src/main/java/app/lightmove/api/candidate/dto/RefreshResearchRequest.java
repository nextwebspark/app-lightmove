package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The people whose research is asked for again; each is one paid read. */
public record RefreshResearchRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> personIds) {}
