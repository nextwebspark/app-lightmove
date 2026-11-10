package app.lightmove.api.outreach.service;

import app.lightmove.api.account.service.AccountDeletionContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A deleted account's outreach of its own: its working hours, its email signatures and any mailbox connect left
 * half-way go with it, and once the deletion commits its mailbox and Zoom account are let go at the provider. Its runs
 * then stop at their next send, which finds the mailbox gone; what it sent stays in each executive's conversation, the
 * workspace's record.
 */
@Component
@RequiredArgsConstructor
class AccountDeletionOutreach implements AccountDeletionContributor {

    private final NamedParameterJdbcTemplate jdbc;
    private final MailboxService mailboxes;
    private final ZoomService zoom;

    @Override
    public void deleteAccountRowsIn(UUID workspaceId, UUID userId) {
        MapSqlParameterSource member = new MapSqlParameterSource("workspaceId", workspaceId).addValue("userId", userId);
        jdbc.update("DELETE FROM app_lm_member_availability WHERE workspace_id = :workspaceId AND user_id = :userId",
                member);
        jdbc.update("DELETE FROM app_lm_email_signature WHERE workspace_id = :workspaceId AND user_id = :userId",
                member);
        jdbc.update("DELETE FROM app_lm_mailbox_authorization WHERE workspace_id = :workspaceId AND user_id = :userId",
                member);
    }

    @Override
    public void releaseAfterAccountDeleted(UUID workspaceId, UUID userId) {
        mailboxes.disconnectIfConnected(userId, workspaceId);
        zoom.disconnectIfConnected(userId, workspaceId);
    }
}
