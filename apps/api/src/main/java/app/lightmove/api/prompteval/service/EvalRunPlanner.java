package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.ProductionVersion;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.AiModelCatalog;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRepeatsReason;
import app.lightmove.api.prompteval.dto.StartEvalRunRequest;
import app.lightmove.api.prompteval.model.EvalDataset;
import app.lightmove.api.prompteval.model.EvalRunPlan;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import app.lightmove.api.promptregistry.service.PromptCatalogService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Works out what a run would be before it is estimated or started: its two versions, its cases and its repeats. */
@Component
@RequiredArgsConstructor
class EvalRunPlanner {

    /** How often a case is answered once one answer says too little. */
    static final int REPEATS_WHEN_ANSWERS_VARY = 3;

    private final EvalDatasetService datasets;
    private final EvalSubjects subjects;
    private final PromptCatalogService catalog;
    private final PromptRegistryStore registry;
    private final AiModelCatalog models;
    private final Clock clock;

    EvalRunPlan plan(StartEvalRunRequest request) {
        EvalDataset dataset = datasets.require(request.datasetId());
        EvalSubject subject = subjects.of(dataset.promptId())
                .orElseThrow(() -> ApiException.of(ErrorCode.EVAL_SUBJECT_UNAVAILABLE));
        PlatformPrompt prompt = catalog.require(dataset.promptId());
        ServedPrompt candidate = versionOf(prompt, request.candidateVersion(), "candidateVersion");
        int baselineVersion = request.baselineVersion() != null ? request.baselineVersion()
                : registry.productionOf(prompt.getPromptId()).map(ProductionVersion::version)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                                "Prompt " + prompt.getPromptId() + " has no version serving"));
        if (baselineVersion == candidate.version()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "baselineVersion",
                    "Choose another version to compare it against");
        }
        ServedPrompt baseline = versionOf(prompt, baselineVersion, "baselineVersion");

        boolean variesByTemperature = isAboveZero(prompt, candidate) || isAboveZero(prompt, baseline);
        int repeats = Math.max(subject.minimumRepeats(), variesByTemperature ? REPEATS_WHEN_ANSWERS_VARY : 1);
        EvalRepeatsReason reason = subject.minimumRepeats() >= repeats && repeats > 1 ? EvalRepeatsReason.PROMPT_VARIES
                : variesByTemperature ? EvalRepeatsReason.TEMPERATURE : EvalRepeatsReason.ONCE;
        int cases = request.split() == EvalCaseSplit.HOLDOUT ? dataset.holdout() : dataset.dev();
        return new EvalRunPlan(dataset, subject, request.split(), candidate, baseline, cases, repeats, reason);
    }

    /** The temperature a version is answered at: its own, else the one its prompt's calls default to. */
    Double temperatureOf(PlatformPrompt prompt, ServedPrompt version) {
        return version.settings().orElse(catalog.defaultsOf(prompt)).temperature();
    }

    /** A version on a model that has retired since it was saved would be answered by another, and scored as itself. */
    private ServedPrompt versionOf(PlatformPrompt prompt, int version, String field) {
        ServedPrompt served = registry.version(prompt.getPromptId(), version)
                .orElseThrow(() -> ApiException.withField(ErrorCode.VALIDATION_FAILED, field,
                        "That version does not exist"));
        if (served.modelId() != null && models.unusableBecause(served.provider(), served.modelId(), prompt.getKind(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC))).isPresent()) {
            throw ApiException.withField(ErrorCode.PROMPT_MODEL_UNAVAILABLE, field,
                    "That version's model can no longer answer this prompt");
        }
        return served;
    }

    private boolean isAboveZero(PlatformPrompt prompt, ServedPrompt version) {
        Double temperature = temperatureOf(prompt, version);
        return temperature != null && temperature > 0;
    }
}
