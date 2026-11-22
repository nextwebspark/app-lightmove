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
import app.lightmove.api.promptregistry.dto.PromptDetail;
import app.lightmove.api.promptregistry.dto.PutPromptLiveRequest;
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
 */
@Service
@RequiredArgsConstructor
public class PromptPromotionService {

    private final PromptCatalogService catalog;
    private final PromptRegistryStore store;
    private final AiModelCatalog models;
    private final PlatformConfigReload reload;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;

    public PromptDetail putLive(UUID userId, String promptId, PutPromptLiveRequest request,
                                HttpServletRequest httpRequest) {
        PlatformPrompt prompt = catalog.require(promptId);
        int version = request.version();
        String reason = request.reason().strip();
        ServedPrompt target = store.version(promptId, version)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No version " + version + " of prompt " + promptId));
        requireItsModelServes(prompt, target);

        Integer from = transactions.execute(status -> {
            store.lockPrompt(promptId);
            Integer live = store.productionOf(promptId).map(ProductionVersion::version).orElse(null);
            if (live != null && live == version) {
                throw ApiException.of(ErrorCode.PROMPT_ALREADY_LIVE);
            }
            store.moveProduction(promptId, live, version, userId, reason);
            reload.bump(RegistryPromptSource.SCOPE);
            return live;
        });
        boolean rollback = from != null && version < from;
        audit.event(rollback ? PlatformEventType.PROMPT_ROLLED_BACK : PlatformEventType.PROMPT_PUT_LIVE)
                .actor(userId)
                .target("prompt", promptId)
                .detail("version", version)
                .detailIfPresent("fromVersion", from)
                .detailIfPresent("model", target.modelId())
                .detail("reason", reason)
                .from(httpRequest)
                .record();
        return catalog.get(promptId);
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
