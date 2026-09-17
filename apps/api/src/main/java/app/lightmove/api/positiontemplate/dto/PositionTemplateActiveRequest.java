package app.lightmove.api.positiontemplate.dto;

import jakarta.validation.constraints.NotNull;

/** Archive ({@code false}) or restore ({@code true}) a library template. */
public record PositionTemplateActiveRequest(@NotNull(message = "Say whether it is active") Boolean active) {
}
