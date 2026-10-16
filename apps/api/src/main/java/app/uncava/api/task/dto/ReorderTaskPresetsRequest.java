package app.uncava.api.task.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Every live preset of the workspace, in the order the catalog now reads; retired ones keep their place after. */
public record ReorderTaskPresetsRequest(@NotNull List<UUID> presetIds) {}
