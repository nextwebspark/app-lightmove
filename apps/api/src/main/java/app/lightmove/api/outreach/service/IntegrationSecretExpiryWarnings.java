package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Warns a workspace's admins by email 30 days and 7 days before its own app's client secret expires, and on the day
 * it does. Each threshold warns once per expiry date; Settings → Integrations says the same on the card.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationSecretExpiryWarnings {

    static final List<Integer> THRESHOLDS = List.of(30, 7, 0);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final WorkspaceMailIntegrationRepository integrations;
    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    private final UserRepository users;
    private final EmailTemplates templates;
    private final EmailSender emailSender;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${lightmove.outreach.secret-expiry-check}", zone = "UTC")
    public void warn() {
        warnAt(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    /** One pass. The timer calls it with today; tests call it with the day they arranged. */
    public void warnAt(LocalDate today) {
        LocalDate horizon = today.plusDays(THRESHOLDS.getFirst());
        for (WorkspaceMailIntegration integration : integrations.findByModeAndSecretExpiresOnLessThanEqual(
                CredentialMode.OWN, horizon)) {
            try {
                warnIfDue(integration, today);
            } catch (RuntimeException failed) {
                log.error("The secret expiry warning for integration {} failed", integration.getId(), failed);
            }
        }
    }

    private void warnIfDue(WorkspaceMailIntegration integration, LocalDate today) {
        OptionalInt threshold = integration.expiryWarningDue(today, THRESHOLDS);
        if (threshold.isEmpty() || integrations.claimExpiryWarning(integration.getId(),
                integration.getSecretExpiresOn(), threshold.getAsInt()) == 0) {
            return;
        }
        String workspaceName = workspaces.findById(integration.getWorkspaceId()).map(Workspace::getName)
                .orElse("your workspace");
        long daysLeft = ChronoUnit.DAYS.between(today, integration.getSecretExpiresOn());
        String link = properties.web().baseUrl() + "/settings/integrations";
        List<UUID> admins = members.findUserIdsHoldingAction(integration.getWorkspaceId(),
                WorkspaceAction.WORKSPACE_MANAGE.name(), MemberStatus.ACTIVE);
        for (User admin : users.findAllById(admins)) {
            emailSender.send(templates.buildIntegrationSecretExpiryEmail(admin.getEmail(), admin.getFullName(),
                    workspaceName, integration.getProvider().displayName(),
                    DATE.format(integration.getSecretExpiresOn()), daysLeft, link));
        }
    }
}
