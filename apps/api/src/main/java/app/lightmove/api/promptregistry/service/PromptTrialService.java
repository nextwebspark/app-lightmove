package app.lightmove.api.promptregistry.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.core.llm.service.PromptTrialRunner;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.promptregistry.dto.PromptTrialResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Try it: one version of a prompt, asked once with its fictional input through the feature's own call path, on
 * that version's model and settings. No transaction is open while the model is asked.
 */
@Service
@RequiredArgsConstructor
public class PromptTrialService {

    private final PromptCatalogService catalog;
    private final PromptRegistryStore store;
    private final PromptTrialRunner runner;
    private final LlmBudgetGuard budget;
    private final AuditService audit;

    public PromptTrialResponse tryIt(UUID userId, String promptId, int version, HttpServletRequest httpRequest) {
        catalog.require(promptId);
        PromptTrial trial = catalog.trialOf(promptId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PROMPT_TRIAL_UNAVAILABLE));
        ServedPrompt tried = store.version(promptId, version)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No version " + version + " of prompt " + promptId));
        budget.require(LlmBudget.PROMPT_TRIAL, userId);

        audit.event(PlatformEventType.PROMPT_TRIED).actor(userId)
                .target("prompt", promptId)
                .detail("version", version)
                .detailIfPresent("model", tried.modelId())
                .from(httpRequest)
                .record();
        PromptTrialResult result = runner.run(trial, tried);
        // A feature that could not reach the model answers "nothing to show", as it does on its own screen.
        if (result.rawAnswer() == null) {
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
        return new PromptTrialResponse(version, result.rawAnswer(), result.parsedAnswer(), result.model(),
                result.inputTokens(), result.outputTokens(), result.finish(), result.tookMillis());
    }
}
