package app.lightmove.api.notification.service;

import app.lightmove.api.account.service.AccountDeletionContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** A deleted account's bell — its notifications, its settings for them and its digests — goes with it. */
@Component
@RequiredArgsConstructor
class AccountDeletionNotifications implements AccountDeletionContributor {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void deleteAccountRowsIn(UUID workspaceId, UUID userId) {
        MapSqlParameterSource member = new MapSqlParameterSource("workspaceId", workspaceId).addValue("userId", userId);
        jdbc.update("DELETE FROM app_lm_notification WHERE workspace_id = :workspaceId AND recipient_user_id = :userId",
                member);
        jdbc.update("DELETE FROM app_lm_notification_preference WHERE workspace_id = :workspaceId AND user_id = :userId",
                member);
        jdbc.update("DELETE FROM app_lm_notification_digest WHERE workspace_id = :workspaceId AND user_id = :userId",
                member);
    }
}
