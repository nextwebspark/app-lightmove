package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fair use for People Search: a page counts the first time a workspace reads it, whoever bought it, so the key is the
 * page's cache key — the same question in every workspace.
 */
@Component
@RequiredArgsConstructor
class PeoplePageMeter {

    private final FairUseGuard fairUse;
    private final UsageRecorder usage;

    /**
     * Checks a page this workspace has not read against its ceiling, and answers the key to record it under; a page
     * already read answers empty and is never refused.
     */
    Optional<String> admit(UUID workspaceId, UUID userId, String pageKey, int profiles) {
        String key = "people-page:" + pageKey;
        if (usage.hasRecorded(workspaceId, key)) {
            return Optional.empty();
        }
        fairUse.check(workspaceId, userId, UsageKind.PEOPLE_SEARCH_PAGE, profiles);
        return Optional.of(key);
    }

    void record(UUID workspaceId, UUID userId, UUID projectId, String key, int profiles) {
        usage.record(new MeteredUse(workspaceId, userId, projectId, UsageKind.PEOPLE_SEARCH_PAGE, profiles, key));
    }
}
