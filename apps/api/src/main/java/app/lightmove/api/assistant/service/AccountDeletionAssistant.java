package app.lightmove.api.assistant.service;

import app.lightmove.api.account.service.AccountDeletionContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** A deleted account's chats with the assistant are one person's, not the firm's, so they go with it (turns cascade). */
@Component
@RequiredArgsConstructor
class AccountDeletionAssistant implements AccountDeletionContributor {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void deleteAccountRowsIn(UUID workspaceId, UUID userId) {
        jdbc.update("DELETE FROM app_lm_assistant_thread WHERE workspace_id = :workspaceId AND user_id = :userId",
                new MapSqlParameterSource("workspaceId", workspaceId).addValue("userId", userId));
    }
}
