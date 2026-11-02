package app.lightmove.api.platform.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import app.lightmove.api.core.platformconfig.service.PlatformConfigChanges;
import app.lightmove.api.core.platformconfig.service.ShippedPrompts;
import app.lightmove.api.platform.dto.PlatformPromptDetail;
import app.lightmove.api.platform.dto.PlatformPromptSummary;
import app.lightmove.api.platform.dto.PlatformPromptVersionResponse;
import app.lightmove.api.platform.dto.PlatformPromptsResponse;
import app.lightmove.api.platform.model.StoredPromptVersion;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Platform → Prompts. A prompt's placeholders are its contract with the code that fills it, so an override
 * keeps exactly the shipped set, checked on every save and again on every restore (a later release may have changed
 * the shipped set since that version was saved). The body is never audited: only the prompt's id and version.
 */
@Service
@RequiredArgsConstructor
public class PlatformPromptService {

    private final PlatformPromptStore store;
    private final ShippedPrompts shipped;
    private final PlatformConfigChanges changes;
    private final PlatformConfigService config;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PlatformPromptsResponse list() {
        Map<String, StoredPromptVersion> active = store.activeVersions().stream()
                .collect(Collectors.toMap(StoredPromptVersion::promptId, Function.identity()));
        List<PlatformPromptSummary> prompts = Arrays.stream(PlatformPrompt.values()).map(prompt -> {
            StoredPromptVersion inUse = active.get(prompt.id());
            return new PlatformPromptSummary(prompt.id(), prompt.label(), inUse != null,
                    inUse == null ? null : inUse.version(), variablesOf(prompt),
                    inUse == null ? null : inUse.createdByName(), inUse == null ? null : inUse.createdAt());
        }).toList();
        return new PlatformPromptsResponse(prompts, config.version());
    }

    @Transactional(readOnly = true)
    public PlatformPromptDetail get(String promptId) {
        PlatformPrompt prompt = require(promptId);
        List<StoredPromptVersion> versions = store.versionsOf(prompt.id());
        Integer activeVersion = versions.stream().filter(StoredPromptVersion::active)
                .map(StoredPromptVersion::version).findFirst().orElse(null);
        return new PlatformPromptDetail(prompt.id(), prompt.label(), prompt.resource(), variablesOf(prompt),
                shipped.of(prompt), store.activeBodyOf(prompt.id()).orElse(null), activeVersion,
                versions.stream().map(version -> new PlatformPromptVersionResponse(version.version(),
                        version.active(), version.createdByName(), version.createdAt())).toList(),
                config.version());
    }

    @Transactional
    public PlatformPromptDetail save(UUID userId, String promptId, String body, Integer expectedVersion,
                                     HttpServletRequest request) {
        PlatformPrompt prompt = require(promptId);
        refuseBlank(body);
        refuseChangedPlaceholders(prompt, body);
        changes.announce();
        Optional<Integer> inUse = store.activeVersionOf(prompt.id());
        if (!Objects.equals(inUse.orElse(null), expectedVersion)) {
            throw new ApiException(ErrorCode.PLATFORM_PROMPT_STALE,
                    "Prompt " + prompt.id() + " is at " + inUse.orElse(null) + ", not " + expectedVersion);
        }
        int version = store.saveActive(prompt.id(), body, userId);
        audit.event(PlatformEventType.PLATFORM_PROMPT_SAVED).actor(userId).target("platform_prompt", prompt.id())
                .detail("version", version).detailIfPresent("replaced", inUse.orElse(null)).from(request).record();
        return get(prompt.id());
    }

    @Transactional
    public PlatformPromptDetail restore(UUID userId, String promptId, int fromVersion, HttpServletRequest request) {
        PlatformPrompt prompt = require(promptId);
        changes.announce();
        String body = store.bodyOf(prompt.id(), fromVersion).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                "Prompt " + prompt.id() + " has no version " + fromVersion));
        refuseChangedPlaceholders(prompt, body);
        int version = store.saveActive(prompt.id(), body, userId);
        audit.event(PlatformEventType.PLATFORM_PROMPT_RESTORED).actor(userId).target("platform_prompt", prompt.id())
                .detail("version", version).detail("restoredFrom", fromVersion).from(request).record();
        return get(prompt.id());
    }

    @Transactional
    public PlatformPromptDetail revert(UUID userId, String promptId, HttpServletRequest request) {
        PlatformPrompt prompt = require(promptId);
        Optional<Integer> inUse = store.activeVersionOf(prompt.id());
        if (inUse.isEmpty()) {
            return get(prompt.id());
        }
        changes.announce();
        store.deactivate(prompt.id());
        audit.event(PlatformEventType.PLATFORM_PROMPT_REVERTED).actor(userId).target("platform_prompt", prompt.id())
                .detail("replaced", inUse.get()).from(request).record();
        return get(prompt.id());
    }

    private void refuseChangedPlaceholders(PlatformPrompt prompt, String body) {
        Set<String> expected = ShippedPrompts.placeholdersIn(shipped.of(prompt));
        Set<String> found = ShippedPrompts.placeholdersIn(body);
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(found);
        Set<String> added = new TreeSet<>(found);
        added.removeAll(expected);
        if (!missing.isEmpty() || !added.isEmpty()) {
            throw ApiException.withProperties(ErrorCode.PLATFORM_PROMPT_VARIABLES_CHANGED,
                    Map.of("missing", List.copyOf(missing), "added", List.copyOf(added)));
        }
    }

    private static void refuseBlank(String body) {
        if (body == null || body.isBlank()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "body", "Write the prompt");
        }
    }

    private List<String> variablesOf(PlatformPrompt prompt) {
        return List.copyOf(ShippedPrompts.placeholdersIn(shipped.of(prompt)));
    }

    private static PlatformPrompt require(String promptId) {
        return PlatformPrompt.byId(promptId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No platform prompt " + promptId));
    }
}
