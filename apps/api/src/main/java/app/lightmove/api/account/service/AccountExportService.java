package app.lightmove.api.account.service;

import app.lightmove.api.account.dto.AccountExportResponse;
import app.lightmove.api.account.dto.ExportedAccount;
import app.lightmove.api.account.dto.ExportedConsents;
import app.lightmove.api.account.dto.ExportedMembership;
import app.lightmove.api.account.dto.ExportedSignIn;
import app.lightmove.api.account.dto.ExportedSignInMethod;
import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyService;
import app.lightmove.api.core.security.mfa.MfaService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.token.ActiveSessionService;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Profile's Download my data: the account as one JSON document. Each workspace's keys, AI apps and sign-in
 * lines are read in that workspace's own scope — outside it they would read as none, and the file would say the
 * person had nothing.
 */
@Service
@RequiredArgsConstructor
public class AccountExportService {

    private static final Duration SIGN_IN_HISTORY = Duration.ofDays(365);
    private static final int SIGN_IN_LIMIT = 500;
    private static final List<String> SIGN_IN_EVENTS = Stream.of(
                    AuthEventType.LOGIN_SUCCEEDED, AuthEventType.OAUTH_LOGIN_SUCCEEDED, AuthEventType.LOGIN_FAILED,
                    AuthEventType.ACCOUNT_LOCKED, AuthEventType.MFA_CHALLENGE_FAILED,
                    AuthEventType.MFA_RECOVERY_CODE_USED, AuthEventType.PASSWORD_CHANGED,
                    AuthEventType.PASSWORD_RESET_COMPLETED, AuthEventType.TOKEN_REUSE_DETECTED,
                    AuthEventType.EXTENSION_PAIRED)
            .map(AuditEventType::code).toList();

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    private final MfaService mfa;
    private final ActiveSessionService sessions;
    private final ApiKeyService apiKeys;
    private final OAuthGrantService oauthGrants;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountExportResponse export(UUID userId, String presentedRefreshToken, HttpServletRequest request) {
        // A deleted account's access token outlives it by up to its fifteen minutes; there is nothing left to export.
        User user = users.findById(userId).filter(found -> !found.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        List<WorkspaceMember> memberships = members.findAllByUserIdOrderByJoinedAtAsc(userId);
        Map<UUID, String> names = workspaces.findAllById(
                        memberships.stream().map(WorkspaceMember::getWorkspaceId).toList()).stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));

        Instant now = Instant.now(clock);
        AccountExportResponse export = new AccountExportResponse(
                now,
                accountOf(user),
                new ExportedConsents(user.getTermsAcceptedAt(), user.getPrivacyPolicyVersion()),
                user.hasPassword(),
                identities.findByUserIdOrderByLinkedAtAsc(userId).stream()
                        .filter(identity -> !UserIdentity.LOCAL_PROVIDER.equals(identity.getProvider()))
                        .map(identity -> new ExportedSignInMethod(identity.getProvider().toLowerCase(Locale.ROOT),
                                identity.getEmail(), identity.getLinkedAt()))
                        .toList(),
                mfa.status(userId),
                memberships.stream().map(member -> membershipOf(member, names, userId)).toList(),
                sessions.list(userId, presentedRefreshToken),
                signInsOf(userId, memberships, now));

        audit.event(AuthEventType.ACCOUNT_DATA_EXPORTED).actor(userId).from(request).record();
        return export;
    }

    private ExportedAccount accountOf(User user) {
        return new ExportedAccount(user.getId(), user.getEmail(), user.getFullName(), user.getTitle(),
                user.getAvatarUrl(), user.getTimezone(), user.getLocale(), user.getStatus().name(),
                user.getCreatedAt(), user.getEmailVerifiedAt(), user.getLastLoginAt());
    }

    private ExportedMembership membershipOf(WorkspaceMember member, Map<UUID, String> names, UUID userId) {
        UUID workspaceId = member.getWorkspaceId();
        return new ExportedMembership(
                workspaceId,
                names.get(workspaceId),
                member.getRoles().stream().map(Role::getName).sorted().toList(),
                member.getStatus().name(),
                member.getJoinedAt(),
                inWorkspace(workspaceId, () -> apiKeys.list(userId, workspaceId, false)),
                inWorkspace(workspaceId, () -> oauthGrants.list(userId, workspaceId, false)));
    }

    /**
     * The audit trail is read in each workspace the person was in and once with none, which is what its read policy
     * shows of an account: the lines of that workspace and the account's own. A line seen twice is kept once.
     */
    private List<ExportedSignIn> signInsOf(UUID userId, List<WorkspaceMember> memberships, Instant now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId)
                .addValue("events", SIGN_IN_EVENTS)
                .addValue("since", Timestamp.from(now.minus(SIGN_IN_HISTORY)))
                .addValue("limit", SIGN_IN_LIMIT);
        Map<Long, ExportedSignIn> seen = new LinkedHashMap<>();
        Supplier<Map<Long, ExportedSignIn>> read = () -> jdbc.query("""
                SELECT id, occurred_at, event_type, outcome, ip_address, user_agent, workspace_id
                FROM app_lm_audit_event
                WHERE actor_user_id = :userId AND event_type IN (:events) AND occurred_at >= :since
                ORDER BY occurred_at DESC
                LIMIT :limit
                """, parameters, (rs, rowNum) -> Map.entry(rs.getLong("id"), signInOf(rs))).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (kept, same) -> kept));
        seen.putAll(read.get());
        memberships.stream().map(WorkspaceMember::getWorkspaceId).distinct()
                .forEach(workspaceId -> seen.putAll(inWorkspace(workspaceId, read)));
        return seen.values().stream()
                .sorted(Comparator.comparing(ExportedSignIn::occurredAt).reversed())
                .limit(SIGN_IN_LIMIT)
                .toList();
    }

    private static ExportedSignIn signInOf(ResultSet rs) throws SQLException {
        return new ExportedSignIn(
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("event_type"),
                rs.getString("outcome"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getObject("workspace_id", UUID.class));
    }

    /** Joined: the export is one read of the account, wherever its rows are. */
    private static <T> T inWorkspace(UUID workspaceId, Supplier<T> task) {
        AtomicReference<T> answer = new AtomicReference<>();
        WorkspaceScope.runInJoinedTransaction(workspaceId, () -> answer.set(task.get()));
        return answer.get();
    }
}
