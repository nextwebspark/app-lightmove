package app.lightmove.api.core.security.apikey;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PublicApiSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.ApiKeyResponse;
import app.lightmove.api.core.security.dto.CreateApiKeyRequest;
import app.lightmove.api.core.security.dto.CreatedApiKeyResponse;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Making, listing and revoking API keys. A personal key is any staff member's own; a workspace key, the
 * whole workspace's list and anyone else's key ask {@code WORKSPACE_MANAGE}. A key that is not the
 * caller's to see answers {@code API_KEY_NOT_FOUND}, the same as one that does not exist.
 */
@Service
public class ApiKeyService {

    private static final String KEY_TARGET = "api_key";

    private final ApiKeyRepository keys;
    private final UserRepository users;
    private final WorkspaceAccess access;
    private final AuditService audit;
    private final Clock clock;
    private final PublicApiSettings settings;

    public ApiKeyService(ApiKeyRepository keys, UserRepository users, WorkspaceAccess access, AuditService audit,
                         Clock clock, LightMoveProperties properties) {
        this.keys = keys;
        this.users = users;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.settings = properties.publicApi();
    }

    @Transactional
    public CreatedApiKeyResponse create(UUID actorId, UUID workspaceId, CreateApiKeyRequest request,
                                        HttpServletRequest httpRequest) {
        ApiKeyKind kind = ApiValueEnum.parse(ApiKeyKind.class, request.kind(), ApiKeyKind.PERSONAL, "key kind");
        if (kind == ApiKeyKind.SERVICE) {
            access.requireAction(actorId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
        }
        List<ApiKeyScope> scopes = scopesOf(request.scopes());
        Instant now = clock.instant();
        Instant expiresAt = now.plus(termOf(request.expiresInDays()));
        if (kind == ApiKeyKind.PERSONAL) {
            access.lockActiveMember(actorId, workspaceId);
            if (keys.countLivePersonal(workspaceId, actorId, now) >= settings.maxActiveKeysPerUser()) {
                throw ApiException.of(ErrorCode.API_KEY_LIMIT_REACHED);
            }
        }

        MintedApiKey minted = ApiKeySecrets.mint(kind);
        ApiKey key = keys.save(ApiKey.issued(workspaceId, kind, actorId, request.name().strip(), minted, scopes,
                expiresAt));

        audit.event(WorkspaceEventType.API_KEY_CREATED).actor(actorId).workspace(workspaceId)
                .target(KEY_TARGET, key.getId()).from(httpRequest)
                .detail("kind", kind.name())
                .detail("scopes", String.join(",", key.getScopes()))
                .detail("expiresAt", expiresAt.toString())
                .record();
        return new CreatedApiKeyResponse(toDtos(List.of(key)).getFirst(), minted.secret());
    }

    /** The caller's own personal keys, or with {@code all} every key in the workspace. Newest first. */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(UUID actorId, UUID workspaceId, boolean all) {
        if (all) {
            access.requireAction(actorId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
            return toDtos(keys.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
        }
        return toDtos(keys.findByWorkspaceIdAndOwnerUserIdOrderByCreatedAtDesc(workspaceId, actorId));
    }

    /** Idempotent: revoking a revoked key changes nothing and records nothing. */
    @Transactional
    public void revoke(UUID actorId, UUID workspaceId, UUID keyId, HttpServletRequest httpRequest) {
        ApiKey key = keys.findByIdAndWorkspaceId(keyId, workspaceId)
                .filter(found -> found.isOwnedBy(actorId)
                        || access.holdsAction(actorId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE))
                .orElseThrow(() -> ApiException.of(ErrorCode.API_KEY_NOT_FOUND));
        if (key.revoke(actorId, ApiKeyRevokeReason.REVOKED, clock.instant())) {
            recordRevoked(actorId, key, httpRequest);
        }
    }

    /**
     * A member's personal keys die with their membership, in the removal's own transaction. The public API
     * re-reads membership on every call as well; this makes the list say so and keeps a later rejoin from
     * reviving keys made before it.
     */
    @Transactional
    public void revokeOnMembershipEnd(UUID actorId, UUID workspaceId, UUID memberUserId,
                                      HttpServletRequest httpRequest) {
        revokeAll(actorId, keys.findByWorkspaceIdAndOwnerUserIdAndRevokedAtIsNull(workspaceId, memberUserId),
                ApiKeyRevokeReason.MEMBER_REMOVED, httpRequest);
    }

    @Transactional
    public void revokeOnWorkspaceDeletion(UUID actorId, UUID workspaceId, HttpServletRequest httpRequest) {
        revokeAll(actorId, keys.findByWorkspaceIdAndRevokedAtIsNull(workspaceId),
                ApiKeyRevokeReason.WORKSPACE_DELETED, httpRequest);
    }

    private void revokeAll(UUID actorId, List<ApiKey> live, ApiKeyRevokeReason reason,
                           HttpServletRequest httpRequest) {
        Instant now = clock.instant();
        live.stream()
                .filter(key -> key.revoke(actorId, reason, now))
                .forEach(key -> recordRevoked(actorId, key, httpRequest));
    }

    private void recordRevoked(UUID actorId, ApiKey key, HttpServletRequest httpRequest) {
        audit.event(WorkspaceEventType.API_KEY_REVOKED).actor(actorId).workspace(key.getWorkspaceId())
                .target(KEY_TARGET, key.getId()).from(httpRequest)
                .detail("kind", key.getKind().name())
                .detail("reason", key.getRevokedReason().name())
                .detailIfPresent("ownerUserId", key.getOwnerUserId())
                .record();
    }

    /** Deduplicated, in the enum's order, so two keys asking the same thing store the same list. */
    private static List<ApiKeyScope> scopesOf(Collection<String> tokens) {
        Set<ApiKeyScope> asked = tokens.stream()
                .map(token -> ApiValueEnum.require(ApiKeyScope.class, token, "scope"))
                .collect(Collectors.toSet());
        if (ApiKeyScope.dataScopes().stream().noneMatch(asked::contains)) {
            throw ApiException.of(ErrorCode.API_KEY_READS_NOTHING);
        }
        return Arrays.stream(ApiKeyScope.values()).filter(asked::contains).toList();
    }

    private Duration termOf(Integer expiresInDays) {
        if (expiresInDays == null) {
            return settings.defaultKeyTtl();
        }
        Duration term = Duration.ofDays(expiresInDays);
        if (term.compareTo(settings.maxKeyTtl()) > 0) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Choose an expiry of " + settings.maxKeyTtl().toDays() + " days or less");
        }
        return term;
    }

    private List<ApiKeyResponse> toDtos(List<ApiKey> page) {
        Set<UUID> people = page.stream()
                .flatMap(key -> Stream.of(key.getOwnerUserId(), key.getCreatedBy(), key.getRevokedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = users.findAllById(people).stream()
                .collect(Collectors.toMap(User::getId, user -> Objects.toString(user.getFullName(), user.getEmail())));
        Instant now = clock.instant();
        return page.stream().map(key -> new ApiKeyResponse(
                key.getId(), key.getName(), key.getKind().name(), key.statusAt(now).name(), key.getTokenHint(),
                List.copyOf(key.getScopes()), key.getOwnerUserId(), nameOf(names, key.getOwnerUserId()),
                nameOf(names, key.getCreatedBy()), key.getCreatedAt(), key.getExpiresAt(), key.getLastUsedAt(),
                key.getLastUsedIp(), key.getRevokedAt(), nameOf(names, key.getRevokedBy()),
                key.getRevokedReason() == null ? null : key.getRevokedReason().name())).toList();
    }

    private static String nameOf(Map<UUID, String> names, UUID userId) {
        return userId == null ? null : names.get(userId);
    }
}
