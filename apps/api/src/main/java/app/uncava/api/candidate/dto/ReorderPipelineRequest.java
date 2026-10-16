package app.uncava.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** Every live stage, or every live status of one stage, in its new order. */
public record ReorderPipelineRequest(@NotEmpty List<UUID> ids) {}
