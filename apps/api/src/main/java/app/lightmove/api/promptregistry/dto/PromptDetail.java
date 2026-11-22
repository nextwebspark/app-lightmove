package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.model.GenerationSettings;
import java.util.List;

/**
 * A prompt's page: its history, what an edit must keep, and what the feature sends beside it.
 * {@code defaults} are the settings a version gets where it sets none, and {@code defaultModelId} the model a
 * version follows where it names none. {@code userMessageTemplate} and {@code trial} are null where the prompt
 * has no Try it; {@code aiUse} is null where it leaves nothing on a person.
 */
public record PromptDetail(
        PromptOverview prompt,
        List<PromptVersionSummary> versions,
        Integer newestShippedVersion,
        List<String> variables,
        ModelCapability needs,
        String defaultModelId,
        GenerationSettings defaults,
        String userMessageTemplate,
        PromptTrialInput trial,
        PromptAiUse aiUse
) {
}
