package app.lightmove.api.promptregistry.service;

import app.lightmove.api.common.constant.AiUse;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptOrigin;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.LivePrompt;
import app.lightmove.api.core.llm.model.PromptCallSpec;
import app.lightmove.api.core.llm.model.PromptVersion;
import app.lightmove.api.core.llm.service.AiModelCatalog;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.service.ChatModelProvider;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import app.lightmove.api.promptregistry.dto.AiModelResponse;
import app.lightmove.api.promptregistry.dto.PromptAiUse;
import app.lightmove.api.promptregistry.dto.PromptDetail;
import app.lightmove.api.promptregistry.dto.PromptOverview;
import app.lightmove.api.promptregistry.dto.PromptTrialInput;
import app.lightmove.api.promptregistry.dto.PromptVersionBody;
import app.lightmove.api.promptregistry.dto.PromptVersionSummary;
import app.lightmove.api.promptregistry.model.PromotionGateResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the platform console reads of the prompt registry: the list, a prompt's page, a version's text, the models. */
@Service
@RequiredArgsConstructor
public class PromptCatalogService {

    /** How long before a model's stated retirement date the console starts warning. */
    static final int RETIREMENT_WARNING_DAYS = 60;

    private final PromptRegistryStore store;
    private final BundledPrompts bundled;
    private final AiModelCatalog models;
    private final List<PromptTrial> trials;
    private final ChatModelProvider provider;
    private final LightMoveProperties properties;
    private final PromotionGate gate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PromptOverview> list() {
        return store.livePrompts().stream()
                .flatMap(live -> PlatformPrompt.of(live.live().promptId()).map(prompt -> overviewOf(prompt, live)).stream())
                .sorted(Comparator.comparing(PromptOverview::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public PromptDetail get(String promptId) {
        PlatformPrompt prompt = require(promptId);
        LivePrompt live = store.livePrompts().stream()
                .filter(candidate -> candidate.live().promptId().equals(promptId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No version of prompt " + promptId));
        int liveVersion = live.live().version();
        Map<Integer, PromotionGateResult> gates = gate.against(promptId, liveVersion);
        List<PromptVersionSummary> versions = store.versionsOf(promptId).stream()
                .map(version -> summaryOf(version, liveVersion, gates))
                .toList();
        PromptTrial trial = trialOf(promptId).orElse(null);
        return new PromptDetail(
                overviewOf(prompt, live),
                versions,
                live.newestBundledVersion() == 0 ? null : live.newestBundledVersion(),
                PromptContract.variablesOf(bundled.bodyOf(promptId)).stream().sorted().toList(),
                prompt.getKind().needs(),
                defaultModelOf(prompt),
                defaultsOf(prompt),
                trial == null ? null : trial.userMessageTemplate(),
                trial == null ? null : new PromptTrialInput(trial.inputName(), trial.inputDescription()),
                aiUseOf(promptId).orElse(null));
    }

    @Transactional(readOnly = true)
    public PromptVersionBody body(String promptId, int version) {
        require(promptId);
        return store.version(promptId, version)
                .map(served -> new PromptVersionBody(served.version(), served.body()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No version " + version + " of prompt " + promptId));
    }

    public List<AiModelResponse> models() {
        LocalDate today = today();
        return models.all().stream().map(model -> {
            Long days = model.retiresInDaysFrom(today);
            boolean retired = model.isRetiredOn(today);
            return new AiModelResponse(model.provider(), model.modelId(), model.displayName(), model.launchStage(),
                    model.retiresOn(), days, !retired && days != null && days <= RETIREMENT_WARNING_DAYS, retired,
                    model.dataTerms(), model.capabilities(), model.temperature(), model.thinkingBudget(),
                    model.maxOutputTokens());
        }).toList();
    }

    public PlatformPrompt require(String promptId) {
        return PlatformPrompt.of(promptId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No prompt " + promptId));
    }

    Optional<PromptTrial> trialOf(String promptId) {
        return trials.stream().filter(trial -> trial.promptId().equals(promptId)).findFirst();
    }

    /** The model a version of this prompt naming none is answered by: its caller's, else the deployment's. */
    public String defaultModelOf(PlatformPrompt prompt) {
        String declared = specOf(prompt).defaultModel();
        return declared != null ? declared : provider.configuredModel();
    }

    static Optional<PromptAiUse> aiUseOf(String promptId) {
        return Arrays.stream(AiUse.values())
                .filter(use -> use.promptFile() != null && BundledPrompts.idOf(use.promptFile()).equals(promptId))
                .findFirst()
                .map(use -> new PromptAiUse(use.title(), use.logic()));
    }

    /**
     * The spec each caller hands {@code PromptCalls}, built by the same factory from the same settings, so the
     * console cannot show a default the call does not use.
     */
    private PromptCallSpec specOf(PlatformPrompt prompt) {
        String promptId = prompt.getPromptId();
        return switch (prompt.getKind()) {
            case STRUCTURED -> PromptCallSpec.structured(promptId, prompt == PlatformPrompt.EVAL_JUDGE
                    ? properties.platform().eval().pinnedJudgeModel() : null);
            case PROSE -> PromptCallSpec.prose(promptId);
            case TOOL_CALLING -> PromptCallSpec.toolCalling(promptId, promptId, properties.assistant().model(),
                    properties.assistant().generationDefaults());
        };
    }

    /** The settings a version of this prompt gets where it sets none. */
    public GenerationSettings defaultsOf(PlatformPrompt prompt) {
        return specOf(prompt).defaults().orElse(provider.configuredSettings());
    }

    private PromptOverview overviewOf(PlatformPrompt prompt, LivePrompt livePrompt) {
        PromptVersion live = livePrompt.live();
        Optional<AiModel> model = modelOf(live.provider(), live.modelId());
        return new PromptOverview(
                prompt.getPromptId(),
                prompt.getDisplayName(),
                prompt.getDescription(),
                prompt.getKind(),
                live.version(),
                live.origin(),
                live.provider(),
                live.modelId(),
                model.map(AiModel::displayName).orElse(live.modelId()),
                model.map(named -> named.retiresInDaysFrom(today())).map(Long::intValue).orElse(null),
                live.modelId() != null && models.unusableBecause(live.provider(), live.modelId(), prompt.getKind(),
                        today()).isPresent(),
                livePrompt.shippedChangedSinceTheEdit(),
                live.createdAt(),
                live.origin() == PromptOrigin.BUNDLED ? null : live.authorName());
    }

    private PromptVersionSummary summaryOf(PromptVersion version, int liveVersion,
                                           Map<Integer, PromotionGateResult> gates) {
        boolean live = version.version() == liveVersion;
        return new PromptVersionSummary(version.version(), version.origin(), live,
                version.changeNote(), version.createdAt(), version.authorName(), version.provider(),
                version.modelId(),
                modelOf(version.provider(), version.modelId()).map(AiModel::displayName).orElse(version.modelId()),
                version.settings(),
                live ? null : gates.getOrDefault(version.version(), PromotionGateResult.NONE));
    }

    private Optional<AiModel> modelOf(AiProvider provider, String modelId) {
        return modelId == null ? Optional.empty() : models.find(provider, modelId);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
