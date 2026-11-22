package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import java.time.LocalDate;
import java.util.Set;

/**
 * One model a prompt version may name. {@code retiresOn} is the provider's stated earliest retirement date, null
 * when it states none; {@code dataTerms} says where it runs and under which terms.
 */
public record AiModel(
        AiProvider provider,
        String modelId,
        String displayName,
        LocalDate retiresOn,
        String dataTerms,
        Set<ModelCapability> capabilities,
        SettingRange temperature,
        SettingRange thinkingBudget,
        SettingRange maxOutputTokens
) {

    public AiModel {
        capabilities = Set.copyOf(capabilities);
    }

    public boolean isRetiredOn(LocalDate day) {
        return retiresOn != null && !day.isBefore(retiresOn);
    }

    public boolean can(ModelCapability capability) {
        return capabilities.contains(capability);
    }
}
