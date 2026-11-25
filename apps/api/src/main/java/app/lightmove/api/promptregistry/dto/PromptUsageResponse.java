package app.lightmove.api.promptregistry.dto;

import java.util.List;

/**
 * The versions of a prompt that were called or decided on in the last {@code days} UTC days, newest first.
 * {@code decisionsCounted} false: no screen asks a person to keep or change its answer, so there is no kept share.
 */
public record PromptUsageResponse(int days, boolean decisionsCounted, List<PromptVersionUsage> versions) {
}
