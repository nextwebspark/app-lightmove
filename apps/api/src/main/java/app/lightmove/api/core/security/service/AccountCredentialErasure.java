package app.lightmove.api.core.security.service;

import app.lightmove.api.core.storage.service.StorageDeletionQueue;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A deleted account's ways in, deleted rather than revoked: its sessions (with the address and browser each kept),
 * provider sign-ins, pending verification and reset links, its second factor, its stored photo and the copies of it
 * sent emails' signatures carry, whose public links would otherwise still serve them. Account tables all, keyed on the
 * user, so no workspace scope is needed. A session's rows go in one statement, since each points at the one that
 * replaced it.
 */
@Component
@RequiredArgsConstructor
public class AccountCredentialErasure {

    // Concatenated into the DELETE below: a fixed list of our own table names, never anything a caller supplies.
    private static final List<String> USER_KEYED_TABLES = List.of(
            "app_lm_mfa_challenge",
            "app_lm_user_mfa_recovery_code",
            "app_lm_user_mfa_factor",
            "app_lm_verification_token",
            "app_lm_user_identity",
            "app_lm_user_avatar",
            "app_lm_refresh_token");

    private final NamedParameterJdbcTemplate jdbc;
    private final StorageDeletionQueue storageDeletions;

    /** The caller flushes first: a factor its code check just stamped would otherwise be written back after this. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void eraseAll(UUID userId) {
        MapSqlParameterSource user = new MapSqlParameterSource("userId", userId);
        for (String table : USER_KEYED_TABLES) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id = :userId", user);
        }
        eraseSignaturePhotos(user);
    }

    /**
     * The photo's copies in the bucket outlive its row (an email already sent names them), so the account's going is
     * what takes them: the files are queued for the sweeper in this transaction and the rows go with it. A sent email
     * then shows no picture, never the former member's face.
     */
    private void eraseSignaturePhotos(MapSqlParameterSource user) {
        List<String> storageKeys = jdbc.queryForList("""
                DELETE FROM app_lm_email_image WHERE kind = 'PHOTO' AND user_id = :userId
                RETURNING storage_key""", user, String.class);
        storageDeletions.enqueue(storageKeys);
    }
}
