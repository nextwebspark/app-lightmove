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
import app.lightmove.api.subprocessor.model.SubprocessorNoticePublished;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails a published notice to every admin of every live workspace, once each, and retries a failed one with backoff
 * until the notice takes effect. Each email is claimed before it is sent, so a second pass or a second instance sends
 * nothing twice. Logs name ids, never an address.
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(SubprocessorNoticePublished published) {
        announce(published.noticeId(), clock.instant());
    }

    /** One pass over every live workspace. A second pass sends only to an admin the first did not reach. */
    public int announce(UUID noticeId, Instant now) {
        Optional<SubprocessorNotice> notice = notices.find(noticeId);
        if (notice.isEmpty() || notice.get().isInEffectOn(dayOf(now))) {
            return 0;
        }
        int sent = 0;
        for (UUID workspaceId : audience.liveWorkspaceIds()) {
            int[] sentHere = {0};
            try {
                // Joined: once the pool is full this runs on the publishing thread, whose committed connection is
                // still bound and still reported active, and a plain scope refuses another workspace there. Every
                // write below is a transaction of its own.
                WorkspaceScope.runInJoinedTransaction(workspaceId,
                        () -> sentHere[0] = announceIn(notice.get(), workspaceId, now));
            } catch (RuntimeException failed) {
                log.error("Announcing sub-processor notice {} in workspace {} failed; the rest still run", noticeId,
                        workspaceId, failed);
            }
            sent += sentHere[0];
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
            int sent = 0;
            for (OwedSubprocessorNoticeEmail owed : emails.owedIn(workspaceId, now)) {
                Optional<SubprocessorNotice> notice = noticesById.computeIfAbsent(owed.noticeId(), notices::find);
                if (notice.isPresent() && claims.reclaim(owed, now, now.plus(retryAfter(owed.attempts() + 1)))) {
                    sent += resend(notice.get(), owed) ? 1 : 0;
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

    private boolean resend(SubprocessorNotice notice, OwedSubprocessorNoticeEmail owed) {
        Optional<String> workspaceName = audience.liveWorkspaceName(owed.workspaceId());
        Optional<User> admin = workspaceName.isPresent()
                ? audience.adminOf(owed.workspaceId(), owed.userId())
                : Optional.empty();
        if (admin.isEmpty()) {
            claims.giveUp(owed);
            return false;
        }
        return deliver(notice, owed.workspaceId(), workspaceName.get(), admin.get());
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
