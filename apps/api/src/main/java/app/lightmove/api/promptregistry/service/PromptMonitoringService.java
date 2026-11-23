package app.lightmove.api.promptregistry.service;

import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.LiveVersionComparison;
import app.lightmove.api.core.llm.model.ProductionVersion;
import app.lightmove.api.core.llm.model.PromptModelUsage;
import app.lightmove.api.core.llm.model.PromptVersionOutcomes;
import app.lightmove.api.core.llm.service.AiModelCatalog;
import app.lightmove.api.core.llm.service.PromptMonitoringStore;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.promptregistry.dto.PromptLiveHealth;
import app.lightmove.api.promptregistry.dto.PromptModelUsageResponse;
import app.lightmove.api.promptregistry.dto.PromptUsageResponse;
import app.lightmove.api.promptregistry.dto.PromptVersionUsage;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** How each prompt version is doing in production, read from the daily counts. No content exists to read. */
@Service
@RequiredArgsConstructor
public class PromptMonitoringService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 90;

    private final PromptMonitoringStore monitoring;
    private final PromptRegistryStore registry;
    private final AiModelCatalog models;

    PromptUsageResponse usage(String promptId, Integer requestedDays) {
        int days = requestedDays == null ? DEFAULT_DAYS : Math.clamp(requestedDays, 1, MAX_DAYS);
        Integer liveVersion = registry.productionOf(promptId).map(ProductionVersion::version).orElse(null);
        Map<Integer, List<PromptModelUsage>> calls = monitoring.usageOf(promptId, days).stream()
                .collect(Collectors.groupingBy(PromptModelUsage::version));
        Map<Integer, PromptVersionOutcomes> outcomes = monitoring.outcomesOf(promptId, days).stream()
                .collect(Collectors.toMap(PromptVersionOutcomes::version, Function.identity()));
        TreeSet<Integer> versions = new TreeSet<>(Comparator.reverseOrder());
        versions.addAll(calls.keySet());
        versions.addAll(outcomes.keySet());
        return new PromptUsageResponse(days, versions.stream().map(version -> {
            PromptVersionOutcomes decided = outcomes.get(version);
            return new PromptVersionUsage(version, version.equals(liveVersion),
                    decided == null ? 0 : decided.accepted(), decided == null ? 0 : decided.changed(),
                    calls.getOrDefault(version, List.of()).stream().map(this::responseOf).toList());
        }).toList());
    }

    /** Null when the prompt's live version replaced none. */
    PromptLiveHealth liveHealthOf(String promptId) {
        return monitoring.liveVersionComparisonOf(promptId, PromptHealthRules.COMPARISON_DAYS)
                .map(PromptHealthRules::healthOf).orElse(null);
    }

    /** By prompt id; a prompt whose live version replaced none has no entry. */
    Map<String, PromptLiveHealth> liveHealth() {
        return monitoring.liveVersionComparisons(PromptHealthRules.COMPARISON_DAYS).stream()
                .collect(Collectors.toMap(LiveVersionComparison::promptId, PromptHealthRules::healthOf));
    }

    private PromptModelUsageResponse responseOf(PromptModelUsage usage) {
        String modelName = models.find(usage.provider(), usage.modelId()).map(AiModel::displayName)
                .orElse(usage.modelId());
        return new PromptModelUsageResponse(usage.provider(), usage.modelId(), modelName, usage.calls(),
                usage.inputTokens(), usage.outputTokens(), usage.finishStop(), usage.finishMaxTokens(),
                usage.finishSafety(), usage.finishOther(), usage.guardRefusals(), usage.schemaReasks(),
                usage.schemaFailures());
    }
}
