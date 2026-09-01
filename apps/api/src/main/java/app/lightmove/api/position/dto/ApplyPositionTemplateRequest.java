package app.lightmove.api.position.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Draft this brief as that role. The template is named by id rather than by code: a workspace will be
 * able to hold its own template under the same code as the library's, and an id says which was picked.
 */
public record ApplyPositionTemplateRequest(@NotNull UUID templateId) {
}
