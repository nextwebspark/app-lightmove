package app.lightmove.api.promptregistry.dto;

import java.util.List;

/** The versions of a prompt that were called or decided on in the last {@code days} UTC days, newest first. */
public record PromptUsageResponse(int days, List<PromptVersionUsage> versions) {
}
