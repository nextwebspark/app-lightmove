package app.lightmove.api.billing.notice.service;

import app.lightmove.api.billing.notice.constant.BillingNoticeKind;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a billing email, committed before it is sent. Its own transaction: an after-commit listener run on the
 * committing thread would otherwise write into the finished transaction, and the claim would never commit.
 */
@Component
@RequiredArgsConstructor
class BillingNoticeClaims {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(BillingNoticeKind kind, String subjectRef, UUID workspaceId) {
        return jdbc.update("""
                INSERT INTO app_lm_billing_notice (kind, subject_ref, workspace_id) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING""", kind.name(), subjectRef, workspaceId) == 1;
    }
}
