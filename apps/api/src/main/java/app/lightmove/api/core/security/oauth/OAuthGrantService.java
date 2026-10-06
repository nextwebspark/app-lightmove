package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.OAuthGrantResponse;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Settings → Connected AI apps, and the ends a grant shares with an API key. Every staff member sees and disconnects
 * their own; the whole workspace's list and anyone else's grant ask {@code WORKSPACE_MANAGE}, and a grant that is not
 * the caller's to see answers {@code OAUTH_GRANT_NOT_FOUND}, as one that does not exist. Deleting the row is the
 * revocation: the refresh token goes with it, and the MCP server refuses the access token on its next call.
 */
@Service
@RequiredArgsConstructor
public class OAuthGrantService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    /** A grant that has been consented and can still be used or refreshed. */
    private static final String LIVE = """
            g.workspace_id = :workspaceId AND g.consented_at IS NOT NULL
            AND GREATEST(g.code_expires_at, g.access_token_expires_at, g.refresh_token_expires_at) > :now
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final WorkspaceAccess access;
    private final AuditService audit;
    private final JsonMapper json;
    private final Clock clock;
    private final ClientVerification verification;

    @Transactional(readOnly = true)
    public List<OAuthGrantResponse> list(UUID actorId, UUID workspaceId, boolean all) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("now", Timestamp.from(clock.instant()));
        String ownerFilter = "";
        if (all) {
            access.requireAction(actorId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
        } else {
            ownerFilter = " AND g.user_id = :userId";
            parameters.addValue("userId", actorId);
        }
        return jdbc.query("""
                SELECT g.id, g.user_id, g.authorized_scopes::text AS scopes, g.consented_at, g.last_used_at,
                       GREATEST(g.code_expires_at, g.access_token_expires_at, g.refresh_token_expires_at) AS expires_at,
                       c.client_id, c.client_name, c.source, c.logo_uri, c.redirect_uris::text AS redirect_uris,
                       COALESCE(u.full_name, u.email) AS owner_name
                FROM app_lm_oauth_authorization g
                JOIN app_lm_oauth_client c ON c.id = g.client_id
                JOIN app_lm_user u ON u.id = g.user_id
                WHERE
                """ + LIVE + ownerFilter + """

                ORDER BY g.consented_at DESC
                """, parameters, (rs, rowNum) -> toResponse(rs));
    }

    @Transactional
    public void revoke(UUID actorId, UUID workspaceId, UUID grantId, HttpServletRequest httpRequest) {
        List<UUID> owners = jdbc.queryForList("""
                SELECT g.user_id FROM app_lm_oauth_authorization g WHERE g.id = :id AND
                """ + LIVE, new MapSqlParameterSource("id", grantId).addValue("workspaceId", workspaceId)
                .addValue("now", Timestamp.from(clock.instant())), UUID.class);
        UUID owner = owners.stream().findFirst()
                .filter(found -> found.equals(actorId)
                        || access.holdsAction(actorId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE))
                .orElseThrow(() -> ApiException.of(ErrorCode.OAUTH_GRANT_NOT_FOUND));
        delete(actorId, List.of(new GrantRef(grantId, owner, workspaceId)), OAuthGrantRevokeReason.REVOKED,
                httpRequest);
    }

    /** A member's grants end with their membership, in the removal's own transaction, as their API keys do. */
    @Transactional
    public void revokeOnMembershipEnd(UUID actorId, UUID workspaceId, UUID memberUserId,
                                      HttpServletRequest httpRequest) {
        delete(actorId, grantsOf(workspaceId, memberUserId), OAuthGrantRevokeReason.MEMBER_REMOVED,
                httpRequest);
    }

    @Transactional
    public void revokeOnWorkspaceDeletion(UUID actorId, UUID workspaceId, HttpServletRequest httpRequest) {
        delete(actorId, grantsOf(workspaceId, null), OAuthGrantRevokeReason.WORKSPACE_DELETED, httpRequest);
    }

    /**
     * A changed or reset password ends every grant the account holds, in every workspace, as it ends every session:
     * a client connected during a takeover must not keep a month of refresh past the owner taking the account back.
     */
    @Transactional
    public void revokeAllOfUser(UUID actorId, UUID userId, HttpServletRequest httpRequest) {
        delete(actorId, jdbc.query("SELECT id, user_id, workspace_id FROM app_lm_oauth_authorization WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), (rs, rowNum) -> GrantRef.of(rs)),
                OAuthGrantRevokeReason.PASSWORD_CHANGED, httpRequest);
    }

    /** Every grant, consented or still waiting for it: a request mid-consent must not survive its membership either. */
    private List<GrantRef> grantsOf(UUID workspaceId, UUID userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("workspaceId", workspaceId);
        String userFilter = "";
        if (userId != null) {
            userFilter = " AND user_id = :userId";
            parameters.addValue("userId", userId);
        }
        return jdbc.query("SELECT id, user_id, workspace_id FROM app_lm_oauth_authorization"
                        + " WHERE workspace_id = :workspaceId" + userFilter,
                parameters, (rs, rowNum) -> GrantRef.of(rs));
    }

    private void delete(UUID actorId, List<GrantRef> grants, OAuthGrantRevokeReason reason,
                        HttpServletRequest httpRequest) {
        for (GrantRef grant : grants) {
            int deleted = jdbc.update("DELETE FROM app_lm_oauth_authorization WHERE id = :id",
                    new MapSqlParameterSource("id", grant.id()));
            if (deleted == 0) {
                continue;
            }
            audit.event(WorkspaceEventType.OAUTH_GRANT_REVOKED).actor(actorId).workspace(grant.workspaceId())
                    .target(HashingAuthorizationService.GRANT_TARGET, grant.id())
                    .detail("ownerUserId", grant.userId().toString())
                    .reason(reason.name())
                    .from(httpRequest)
                    .record();
        }
    }

    private OAuthGrantResponse toResponse(ResultSet rs) throws SQLException {
        List<String> redirects = json.readValue(rs.getString("redirect_uris"), STRINGS);
        return new OAuthGrantResponse(
                rs.getObject("id", UUID.class),
                rs.getString("client_id"),
                rs.getString("client_name"),
                verification.isVerified(OAuthClientSource.valueOf(rs.getString("source")), rs.getString("client_id")),
                redirects.isEmpty() ? null : hostOf(redirects.getFirst()),
                rs.getString("logo_uri"),
                json.readValue(rs.getString("scopes"), STRINGS),
                rs.getObject("user_id", UUID.class),
                rs.getString("owner_name"),
                instant(rs, "consented_at"),
                instant(rs, "last_used_at"),
                instant(rs, "expires_at"));
    }

    static String hostOf(String uri) {
        try {
            return URI.create(uri).getHost();
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record GrantRef(UUID id, UUID userId, UUID workspaceId) {

        static GrantRef of(ResultSet rs) throws SQLException {
            return new GrantRef(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getObject("workspace_id", UUID.class));
        }
    }
}
