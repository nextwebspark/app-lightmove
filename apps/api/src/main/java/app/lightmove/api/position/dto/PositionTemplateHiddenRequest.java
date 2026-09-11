package app.lightmove.api.position.dto;

import jakarta.validation.constraints.NotNull;

/** Hide a library template from a workspace's picker and title matching, or show it again. */
public record PositionTemplateHiddenRequest(@NotNull(message = "Say whether it is hidden") Boolean hidden) {
}
