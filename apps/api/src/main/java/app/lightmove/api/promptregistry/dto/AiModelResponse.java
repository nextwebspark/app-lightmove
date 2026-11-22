package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.constant.ModelLaunchStage;
import app.lightmove.api.core.llm.model.SettingRange;
import java.time.LocalDate;
import java.util.Set;

/**
 * One model of the catalogue. {@code retiringSoon} from 60 days before the date its provider states until that
 * date, and {@code retired} from it, when a version can no longer be saved on it or put live. The date and
 * {@code retiresInDays} are null when the provider states none.
 */
public record AiModelResponse(
        AiProvider provider,
        String modelId,
        String displayName,
        ModelLaunchStage status,
        LocalDate retiresOn,
        Long retiresInDays,
        boolean retiringSoon,
        boolean retired,
        String dataTerms,
        Set<ModelCapability> capabilities,
        SettingRange temperature,
        SettingRange thinkingBudget,
        SettingRange maxOutputTokens
) {
}
