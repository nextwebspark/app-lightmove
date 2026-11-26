package app.lightmove.api.subprocessor.service;

import app.lightmove.api.common.time.service.DateTimeFormats;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.subprocessor.model.OwedSubprocessorNoticeEmail;
import app.lightmove.api.subprocessor.model.SubprocessorNotice;
import app.lightmove.api.subprocessor.repository.SubprocessorNoticeEmailRepository;
import app.lightmove.api.subprocessor.repository.SubprocessorNoticeRepository;
import app.lightmove.api.workspace.service.DateTimePreferenceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Emails a published notice to every admin of every live workspace, once each, and retries a failed one with backoff
 * until the notice takes effect. Publishing only commits the notice: this scheduled pass sends it, so a request thread
 * never walks the workspaces (the async pool's overflow runs on its submitter, #1502). Each email is claimed before
 * it is sent, so a second pass or a second instance sends nothing twice. Logs name ids, never an address.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubprocessorNoticeMailer {

    static final Duration FIRST_RETRY = Duration.ofHours(1);
    static final Duration LONGEST_RETRY = Duration.ofHours(24);

    private final SubprocessorNoticeRepository notices;
    private final SubprocessorNoticeEmailRepository emails;
    private final SubprocessorNoticeEmailClaims claims;
    private final SubprocessorNoticeAudience audience;
    private final EveryWorkspace everyWorkspace;
    private final TenantResolvers resolvers;
    private final EmailTemplates templates;
    private final EmailSender emailSender;
    private final DateTimePreferenceService dateTimePreferences;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.platform.subprocessor-notice-announce}",
            initialDelayString = "${lightmove.platform.subprocessor-notice-announce}")
    public void scheduledAnnounce() {
        announceAt(clock.instant());
    }

    /**
     * Sends every notice not yet announced to the admins it has not reached. A notice is marked announced only once
     * every live workspace's pass completed, so one that failed is walked again by the next pass.
     */
    public int announceAt(Instant now) {
        int sent = 0;
        for (SubprocessorNotice notice : notices.awaitingAnnouncement()) {
            boolean everyWorkspacePassed = true;
            if (!notice.isInEffectOn(dayOf(now))) {
                for (UUID workspaceId : audience.liveWorkspaceIds()) {
                    try {
                        sent += WorkspaceScope.call(workspaceId, () -> announceIn(notice, workspaceId, now));
                    } catch (RuntimeException failed) {
                        everyWorkspacePassed = false;
                        log.error("Announcing sub-processor notice {} in workspace {} failed; the next pass walks it "
                                + "again", notice.id(), workspaceId, failed);
                    }
                }
            }
            if (everyWorkspacePassed) {
                notices.markAnnounced(notice.id(), now);
            }
        }
        return sent;
    }

    @Scheduled(fixedDelayString = "${lightmove.platform.subprocessor-notice-retry}",
            initialDelayString = "${lightmove.platform.subprocessor-notice-retry}")
    public void scheduledRetry() {
        retryAt(clock.instant());
    }

    /** Resends every email that failed and is due again, in the workspaces V248's resolver names. */
    public int retryAt(Instant now) {
        Map<UUID, Optional<SubprocessorNotice>> noticesById = new HashMap<>();
        return everyWorkspace.sum(resolvers.workspacesWithSubprocessorEmailsOwed(now), workspaceId -> {
            Optional<String> workspaceName = audience.liveWorkspaceName(workspaceId);
            Map<UUID, User> admins = workspaceName.isPresent()
                    ? audience.adminsOf(workspaceId).stream().collect(Collectors.toMap(User::getId, admin -> admin))
                    : Map.of();
            int sent = 0;
            for (OwedSubprocessorNoticeEmail owed : emails.owedIn(workspaceId, now)) {
                Optional<SubprocessorNotice> notice = noticesById.computeIfAbsent(owed.noticeId(), notices::find);
                if (notice.isEmpty() || !claims.reclaim(owed, now, now.plus(retryAfter(owed.attempts() + 1)))) {
                    continue;
                }
                User admin = admins.get(owed.userId());
                if (admin == null) {
                    claims.giveUp(owed);
                } else if (deliver(notice.get(), workspaceId, workspaceName.get(), admin)) {
                    sent++;
                }
            }
            return sent;
        });
    }

    private int announceIn(SubprocessorNotice notice, UUID workspaceId, Instant now) {
        Optional<String> workspaceName = audience.liveWorkspaceName(workspaceId);
        if (workspaceName.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (User admin : audience.adminsOf(workspaceId)) {
            if (claims.claim(notice.id(), workspaceId, admin.getId(), now.plus(retryAfter(1)))
                    && deliver(notice, workspaceId, workspaceName.get(), admin)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean deliver(SubprocessorNotice notice, UUID workspaceId, String workspaceName, User admin) {
        String effectiveOn = DateTimeFormats.of(dateTimePreferences.workspaceDefaultsOf(workspaceId))
                .date(notice.effectiveOn());
        String objectLink = properties.web().baseUrl() + "/settings/data?object=" + notice.id();
        boolean accepted = emailSender.trySend(templates.buildSubprocessorNoticeEmail(admin.getEmail(),
                admin.getFullName(), workspaceName, notice.provider(), notice.receives(), notice.purpose(),
                notice.region(), effectiveOn, notice.listUrl(), objectLink));
        if (accepted) {
            claims.markSent(notice.id(), workspaceId, admin.getId());
        } else {
            log.warn("The sub-processor notice {} to user {} in workspace {} was not accepted; it is retried",
                    notice.id(), admin.getId(), workspaceId);
        }
        return accepted;
    }

    /** One hour after the first try, doubling to a day at most. */
    static Duration retryAfter(int attempt) {
        Duration wait = FIRST_RETRY.multipliedBy(1L << Math.min(Math.max(attempt - 1, 0), 5));
        return wait.compareTo(LONGEST_RETRY) > 0 ? LONGEST_RETRY : wait;
    }

    private static LocalDate dayOf(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC);
    }
}
