package app.lightmove.api.subprocessor.service;

import app.lightmove.api.subprocessor.model.OwedSubprocessorNoticeEmail;
import app.lightmove.api.subprocessor.repository.SubprocessorNoticeEmailRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each write of a notice email in its own transaction, committed before or after the send it is about: an after-commit
 * listener run on the committing thread would otherwise write into the finished transaction (as {@code
 * BillingNoticeClaims} found), and the claim would never commit.
 */
@Component
@RequiredArgsConstructor
class SubprocessorNoticeEmailClaims {

    private final SubprocessorNoticeEmailRepository emails;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID noticeId, UUID workspaceId, UUID userId, Instant retryAt) {
        return emails.claim(noticeId, workspaceId, userId, retryAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reclaim(OwedSubprocessorNoticeEmail owed, Instant now, Instant retryAt) {
        return emails.reclaim(owed, now, retryAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID noticeId, UUID workspaceId, UUID userId) {
        emails.markSent(noticeId, workspaceId, userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void giveUp(OwedSubprocessorNoticeEmail owed) {
        emails.giveUp(owed);
    }
}
