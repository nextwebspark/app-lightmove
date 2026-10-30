package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.StatusAutomationTrigger;
import app.lightmove.api.candidate.dto.StatusAutomationResponse;
import app.lightmove.api.candidate.dto.UpdateStatusAutomationRequest;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which status each outreach event moves a candidate forward to, per workspace (V140): always a status in
 * process. No row is the seeded behaviour, so a workspace that never opened the card works as it always did.
 * A switched-on rule whose status was retired or taken out of process since is reported broken and moves
 * nobody: it never fails the send or booking that fired it.
 */
@Service
@RequiredArgsConstructor
public class StatusAutomationService {

    private final JdbcTemplate jdbc;
    private final CandidatePipelineCatalog catalogs;
    private final AuditService audit;

    /** The live in-process status {@code trigger} moves people to; empty when it is off, unset, retired or out. */
    @Transactional(readOnly = true)
    public Optional<CandidateStatusDefinition> targetOf(UUID workspaceId, StatusAutomationTrigger trigger) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        Rule rule = rulesOf(workspaceId, catalog).get(trigger);
        if (!rule.enabled() || rule.statusId() == null) {
            return Optional.empty();
        }
        return statusIn(catalog, rule.statusId())
                .filter(status -> !status.isRetired() && !status.isOutOfProcess());
    }

    @Transactional(readOnly = true)
    public List<StatusAutomationResponse> automationsOf(UUID workspaceId) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        Map<StatusAutomationTrigger, Rule> rules = rulesOf(workspaceId, catalog);
        return Arrays.stream(StatusAutomationTrigger.values())
                .map(trigger -> toResponse(catalog, trigger, rules.get(trigger)))
                .toList();
    }

    /** The switched-on automations that move people to one of {@code statusIds}: what retiring them would stop. */
    @Transactional(readOnly = true)
    public List<StatusAutomationResponse> pointingAt(UUID workspaceId, Collection<UUID> statusIds) {
        return automationsOf(workspaceId).stream()
                .filter(automation -> automation.enabled() && statusIds.contains(automation.statusId()))
                .toList();
    }

    @Transactional
    public List<StatusAutomationResponse> change(UUID userId, UUID workspaceId, String triggerToken,
                                                 UpdateStatusAutomationRequest request, HttpServletRequest httpRequest) {
        StatusAutomationTrigger trigger = ApiValueEnum.require(StatusAutomationTrigger.class, triggerToken,
                "automation");
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        UUID statusId = request.statusId();
        if (request.enabled() && statusId == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "statusId",
                    "Choose the status this moves people to");
        }
        if (statusId != null) {
            CandidateStatusDefinition status = statusIn(catalog, statusId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            // A paused rule must still be switchable off, so a retired or flagged status is refused only for a rule
            // turned on.
            if (request.enabled() && status.isRetired()) {
                throw ApiException.of(ErrorCode.PIPELINE_STATUS_RETIRED);
            }
            if (request.enabled() && status.isOutOfProcess()) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "statusId",
                        "Choose a status that is in process");
            }
        }
        jdbc.update("""
                INSERT INTO app_lm_workspace_status_automation (workspace_id, trigger, status_id, enabled, updated_by)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id, trigger)
                DO UPDATE SET status_id = excluded.status_id, enabled = excluded.enabled,
                              updated_by = excluded.updated_by, updated_at = now()""",
                workspaceId, trigger.name(), statusId, request.enabled(), userId);
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED).actor(userId).workspace(workspaceId).from(httpRequest)
                .detail("section", "pipeline")
                .detail("change", "automationChanged")
                .detail("trigger", trigger.value())
                .detail("enabled", request.enabled())
                .detailIfPresent("targetId", statusId == null ? null : statusId.toString())
                .record();
        return automationsOf(workspaceId);
    }

    private Map<StatusAutomationTrigger, Rule> rulesOf(UUID workspaceId, PipelineCatalog catalog) {
        Map<StatusAutomationTrigger, Rule> rules = new EnumMap<>(StatusAutomationTrigger.class);
        for (StatusAutomationTrigger trigger : StatusAutomationTrigger.values()) {
            rules.put(trigger, trigger.seededTarget() == null
                    ? new Rule(false, null)
                    : new Rule(true, catalog.bySystemKey(trigger.seededTarget()).getId()));
        }
        jdbc.query("SELECT trigger, status_id, enabled FROM app_lm_workspace_status_automation WHERE workspace_id = ?",
                row -> {
                    rules.put(StatusAutomationTrigger.valueOf(row.getString("trigger")),
                            new Rule(row.getBoolean("enabled"), row.getObject("status_id", UUID.class)));
                }, workspaceId);
        return rules;
    }

    private static StatusAutomationResponse toResponse(PipelineCatalog catalog, StatusAutomationTrigger trigger,
                                                       Rule rule) {
        boolean broken = rule.enabled() && rule.statusId() != null
                && statusIn(catalog, rule.statusId())
                        .map(status -> status.isRetired() || status.isOutOfProcess())
                        .orElse(true);
        return new StatusAutomationResponse(trigger.value(), rule.enabled(), rule.statusId(), broken);
    }

    private static Optional<CandidateStatusDefinition> statusIn(PipelineCatalog catalog, UUID statusId) {
        return catalog.statuses().stream().filter(status -> status.getId().equals(statusId)).findFirst();
    }

    private record Rule(boolean enabled, UUID statusId) {}
}
