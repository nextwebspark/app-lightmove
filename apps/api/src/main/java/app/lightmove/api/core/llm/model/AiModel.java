package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.constant.ModelLaunchStage;
import app.lightmove.api.core.llm.constant.ThinkingLevel;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * One model a prompt version may name. {@code retiresOn} is the provider's stated earliest retirement date, null
 * when it states none; {@code dataTerms} says where it runs and under which terms. {@code thinkingLevels}, least
 * first, are what the model takes in place of a thinking budget; empty for a model that still takes a budget.
 */
public record AiModel(
        AiProvider provider,
        String modelId,
        String displayName,
        ModelLaunchStage launchStage,
        LocalDate retiresOn,
        String dataTerms,
        Set<ModelCapability> capabilities,
        SettingRange temperature,
        SettingRange thinkingBudget,
        SettingRange maxOutputTokens,
        List<ThinkingLevel> thinkingLevels
) {

    public AiModel {
        capabilities = Set.copyOf(capabilities);
        thinkingLevels = List.copyOf(thinkingLevels);
    }

    public boolean isRetiredOn(LocalDate day) {
        return retiresOn != null && !day.isBefore(retiresOn);
    }

    /** Days from {@code day} to the retirement date, negative once past it; null when the provider states none. */
    public Long retiresInDaysFrom(LocalDate day) {
        return retiresOn == null ? null : ChronoUnit.DAYS.between(day, retiresOn);
    }

    public boolean can(ModelCapability capability) {
        return capabilities.contains(capability);
    }
}
