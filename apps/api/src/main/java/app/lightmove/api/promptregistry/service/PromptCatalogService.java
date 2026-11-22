package app.lightmove.api.promptregistry.service;

import app.lightmove.api.candidate.constant.AiUse;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the platform console reads of the prompt registry: the list, a prompt's page, a version's text, the models. */
@Service
public class PromptCatalogService {

    /** How long before a model's stated retirement date the console starts warning. */
    static final int RETIREMENT_WARNING_DAYS = 60;

    private static final String STATUS_GA = "GA";

    private final PromptRegistryStore store;
    private final BundledPrompts bundled;
    private final AiModelCatalog models;
    private final Map<String, PromptTrial> trials;
    private final LightMoveProperties properties;
    private final String chatModel;
    private final Double chatTemperature;
    private final Integer chatMaxOutputTokens;
    private final Clock clock;

    // Hand-written: Lombok cannot put @Value on a generated constructor parameter.
    public PromptCatalogService(PromptRegistryStore store, BundledPrompts bundled, AiModelCatalog models,
                                List<PromptTrial> trials, LightMoveProperties properties, Clock clock,
                                @Value("${spring.ai.google.genai.chat.model:}") String chatModel,
                                @Value("${spring.ai.google.genai.chat.temperature:#{null}}") Double chatTemperature,
                                @Value("${spring.ai.google.genai.chat.max-output-tokens:#{null}}")
                                Integer chatMaxOutputTokens) {
        this.store = store;
        this.bundled = bundled;
        this.models = models;
        this.trials = trials.stream().collect(Collectors.toUnmodifiableMap(PromptTrial::promptId, Function.identity()));
        this.properties = properties;
        this.clock = clock;
        this.chatModel = chatModel;
        this.chatTemperature = chatTemperature;
        this.chatMaxOutputTokens = chatMaxOutputTokens;
    }

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
        List<PromptVersionSummary> versions = store.versionsOf(promptId).stream()
                .map(version -> summaryOf(version, live.live().version()))
                .toList();
        PromptTrial trial = trials.get(promptId);
        return new PromptDetail(
                overviewOf(prompt, live),
                versions,
                live.newestBundledVersion() == 0 ? null : live.newestBundledVersion(),
                PromptContract.variablesOf(bundled.bodyOf(promptId)).stream().sorted().toList(),
                prompt.getKind().needs(),
                specOf(prompt).defaultModel(),
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
            long days = ChronoUnit.DAYS.between(today, model.retiresOn());
            return new AiModelResponse(model.provider(), model.modelId(), model.displayName(), STATUS_GA,
                    model.retiresOn(), days, days <= RETIREMENT_WARNING_DAYS, model.isRetiredOn(today),
                    model.dataTerms(), model.capabilities(), model.temperature(), model.thinkingBudget(),
                    model.maxOutputTokens());
        }).toList();
    }

    PlatformPrompt require(String promptId) {
        return PlatformPrompt.of(promptId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No prompt " + promptId));
    }

    Optional<PromptTrial> trialOf(String promptId) {
        return Optional.ofNullable(trials.get(promptId));
    }

    /** The model and settings a version of this prompt gets where it names none: what its caller declares. */
    PromptCallSpec specOf(PlatformPrompt prompt) {
        return switch (prompt.getKind()) {
            case STRUCTURED -> withTheChatModel(PromptCallSpec.structured(prompt.getPromptId()));
            case PROSE -> withTheChatModel(PromptCallSpec.prose(prompt.getPromptId()));
            case TOOL_CALLING -> PromptCallSpec.toolCalling(prompt.getPromptId(), prompt.getPromptId(),
                    properties.assistant().model(), new GenerationSettings(properties.assistant().temperature(),
                            properties.assistant().thinkingBudget(), null));
        };
    }

    static Optional<PromptAiUse> aiUseOf(String promptId) {
        return Arrays.stream(AiUse.values())
                .filter(use -> use.promptFile() != null && use.promptFile().equals(promptId + "-system.st"))
                .findFirst()
                .map(use -> new PromptAiUse(use.title(), use.logic()));
    }

    private PromptCallSpec withTheChatModel(PromptCallSpec spec) {
        return new PromptCallSpec(spec.promptId(), spec.callLabel(), spec.kind(),
                chatModel == null || chatModel.isBlank() ? null : chatModel, spec.defaults());
    }

    private GenerationSettings defaultsOf(PlatformPrompt prompt) {
        return specOf(prompt).defaults().orElse(new GenerationSettings(chatTemperature, null, chatMaxOutputTokens));
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
                model.map(named -> (int) ChronoUnit.DAYS.between(today(), named.retiresOn())).orElse(null),
                livePrompt.shippedChangedSinceTheEdit(),
                live.createdAt(),
                live.origin() == PromptOrigin.BUNDLED ? null : live.authorName());
    }

    private PromptVersionSummary summaryOf(PromptVersion version, int liveVersion) {
        return new PromptVersionSummary(version.version(), version.origin(), version.version() == liveVersion,
                version.changeNote(), version.createdAt(), version.authorName(), version.provider(),
                version.modelId(),
                modelOf(version.provider(), version.modelId()).map(AiModel::displayName).orElse(version.modelId()),
                version.settings());
    }

    private Optional<AiModel> modelOf(AiProvider provider, String modelId) {
        return modelId == null ? Optional.empty() : models.find(provider, modelId);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
