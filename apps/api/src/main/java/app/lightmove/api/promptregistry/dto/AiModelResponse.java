package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.model.SettingRange;
import java.time.LocalDate;
import java.util.Set;

/**
 * One model of the catalogue. {@code retiringSoon} from 60 days before the date its provider states;
 * {@code retired} from that date, when a version can no longer be saved on it.
 */
public record AiModelResponse(
        AiProvider provider,
        String modelId,
        String displayName,
        String status,
        LocalDate retiresOn,
        long retiresInDays,
        boolean retiringSoon,
        boolean retired,
        String dataTerms,
        Set<ModelCapability> capabilities,
        SettingRange temperature,
        SettingRange thinkingBudget,
        SettingRange maxOutputTokens
) {
}
