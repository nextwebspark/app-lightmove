package app.lightmove.api.task.dto;

import jakarta.validation.constraints.NotNull;

/** Which outreach events file a task by themselves in the workspace. */
public record TaskRulesDto(@NotNull Boolean reply, @NotNull Boolean bounce, @NotNull Boolean bookedCall) {}
