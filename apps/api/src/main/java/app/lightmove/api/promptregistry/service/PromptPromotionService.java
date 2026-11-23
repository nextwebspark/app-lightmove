package app.lightmove.api.promptregistry.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.ProductionVersion;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.AiModelCatalog;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.llm.service.RegistryPromptSource;
import app.lightmove.api.core.reload.PlatformConfigReload;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import app.lightmove.api.promptregistry.constant.PromotionGateState;
import app.lightmove.api.promptregistry.dto.PromptDetail;
import app.lightmove.api.promptregistry.dto.PutPromptLiveRequest;
import app.lightmove.api.promptregistry.model.PromotionGateResult;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves a prompt's production label: the one act that changes what every workspace's AI calls are answered with.
 * A rollback is the same move to an earlier version. The move, its reason and the reload are one transaction, so
 * no instance reloads before the label is committed.
 *
 * <p>A version an eval run on the held-back cases found no worse than the one serving goes live unexplained. Any
 * other move, a rollback among them, needs a written reason.
 */
@Service
@RequiredArgsConstructor
public class PromptPromotionService {

    private final PromptCatalogService catalog;
    private final PromptRegistryStore store;
    private final AiModelCatalog models;
    private final PromotionGate gate;
    private final PlatformConfigReload reload;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;

    public PromptDetail putLive(UUID userId, String promptId, PutPromptLiveRequest request,
                                HttpServletRequest httpRequest) {
        PlatformPrompt prompt = catalog.require(promptId);
        int version = request.version();
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        ServedPrompt target = store.version(promptId, version)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No version " + version + " of prompt " + promptId));
        requireItsModelServes(prompt, target);

        Move move = transactions.execute(status -> {
            store.lockPrompt(promptId);
            Integer live = store.productionOf(promptId).map(ProductionVersion::version).orElse(null);
            if (live != null && live == version) {
                throw ApiException.of(ErrorCode.PROMPT_ALREADY_LIVE);
            }
            boolean rollback = live != null && version < live;
            // Read under the prompt's lock: the run must have scored against the version serving at this moment.
            PromotionGateResult evals = live == null ? PromotionGateResult.NONE
                    : gate.against(promptId, live).getOrDefault(version, PromotionGateResult.NONE);
            if (reason == null && (rollback || evals.state() != PromotionGateState.PASSED)) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "reason",
                        "Say why this version should serve");
            }
            store.moveProduction(promptId, live, version, userId, reason);
            reload.bump(RegistryPromptSource.SCOPE);
            return new Move(live, rollback, evals);
        });
        AuditService.Builder event = audit
                .event(move.rollback() ? PlatformEventType.PROMPT_ROLLED_BACK : PlatformEventType.PROMPT_PUT_LIVE)
                .actor(userId)
                .target("prompt", promptId)
                .detail("version", version)
                .detailIfPresent("fromVersion", move.from())
                .detailIfPresent("model", target.modelId())
                .detailIfPresent("reason", reason)
                .from(httpRequest);
        if (!move.rollback()) {
            event.detail("gate", move.evals().state().name()).detailIfPresent("run", move.evals().runNumber());
        }
        event.record();
        if (!move.rollback() && move.evals().state() == PromotionGateState.FAILED) {
            audit.event(PlatformEventType.EVAL_GATE_OVERRIDDEN)
                    .actor(userId)
                    .target("prompt", promptId)
                    .detail("version", version)
                    .detail("run", move.evals().runNumber())
                    .detail("reason", reason)
                    .from(httpRequest)
                    .record();
        }
        return catalog.get(promptId);
    }

    /** What a move found when it was made. {@code from} is null for a prompt nothing served yet. */
    private record Move(Integer from, boolean rollback, PromotionGateResult evals) {
    }

    /**
     * Saving refused such a model, but a model retires after its versions are saved, and a rollback is the likeliest
     * way back to one: put live, the configured model would quietly answer in its place.
     */
    private void requireItsModelServes(PlatformPrompt prompt, ServedPrompt target) {
        if (target.modelId() == null) {
            return;
        }
        models.unusableBecause(target.provider(), target.modelId(), prompt.getKind(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC))).ifPresent(reason -> {
                    throw ApiException.userFacing(ErrorCode.PROMPT_MODEL_UNAVAILABLE,
                            "That version's model can no longer answer this prompt. Save a new version on another"
                                    + " model, then put that live.",
                            "Version " + target.version() + " of prompt " + prompt.getPromptId() + " names model "
                                    + target.modelId() + ", unusable: " + reason);
                });
    }
}
