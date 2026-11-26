package app.lightmove.api.subprocessor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the workspace objects. Sent again, it replaces the reason given before. */
public record ObjectToSubprocessorRequest(@NotBlank @Size(max = 2000) String reason) {
}
