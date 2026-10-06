package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.token.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The authorization server's grants (V115), with every token held as its SHA-256 only — the state, the code, the access
 * token and the refresh token alike.
 *
 * <p>A token read back carries {@code sha256:<hash>} as its value: nothing the framework does with a stored token needs
 * the secret, and a value already so marked is stored as it stands, so re-saving a grant never hashes a hash.
 *
 * <p>A refresh token rotated away is kept, as a hash, until the grant ends. Presented again it is the theft signature:
 * the grant is deleted and the caller gets nothing, so the thief's copy and the client's newer one both stop working.
 */
@Slf4j
@Component
public class HashingAuthorizationService implements OAuth2AuthorizationService {

    /** The authorization request parameter carrying the workspace the consent screen chose. */
    public static final String WORKSPACE_PARAMETER = "workspace_id";

    static final String HASH_MARKER = "sha256:";
    /** The four hashes a grant held when it was read, carried on it until it is saved back; never stored. */
    static final String LOADED_HASHES = "lightmove.loaded-hashes";
    static final String GRANT_TARGET = "oauth_grant";

    private static final OAuth2TokenType STATE = new OAuth2TokenType(OAuth2ParameterNames.STATE);
    private static final OAuth2TokenType CODE = new OAuth2TokenType(OAuth2ParameterNames.CODE);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private static final String COLUMNS = """
            id, client_id, user_id, workspace_id, authorization_grant_type, authorized_scopes::text AS authorized_scopes,
            attributes, state_hash, code_hash, code_issued_at, code_expires_at, code_metadata,
            access_token_hash, access_token_issued_at, access_token_expires_at,
            access_token_scopes::text AS access_token_scopes, access_token_metadata,
            refresh_token_hash, refresh_token_issued_at, refresh_token_expires_at, refresh_token_metadata
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RegisteredClientRepository clients;
    private final AuditService audit;
    private final Clock clock;
    /** Spring Security's typed mapper, for the framework's own attributes and metadata, as its JDBC service writes them. */
    private final JsonMapper json;
    /** For the scope arrays, plain JSON a query can read. */
    private final JsonMapper plainJson = JsonMapper.builder().build();

    public HashingAuthorizationService(NamedParameterJdbcTemplate jdbc, RegisteredClientRepository clients,
                                       AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.audit = audit;
        this.clock = clock;
        this.json = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(HashingAuthorizationService.class.getClassLoader()))
                .build();
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        UUID id = UUID.fromString(authorization.getId());
        Optional<StoredHashes> before = storedHashes(id);
        refuseIfChangedSinceRead(authorization, before);
        Instant now = clock.instant();

        OAuth2Authorization.Token<OAuth2AuthorizationCode> code = authorization.getToken(OAuth2AuthorizationCode.class);
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization.getAccessToken();
        OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
        String state = authorization.getAttribute(OAuth2ParameterNames.STATE);

        String codeHash = code == null ? null : hashOf(code.getToken().getTokenValue());
        String refreshHash = refresh == null ? null : hashOf(refresh.getToken().getTokenValue());
        boolean consented = codeHash != null && before.map(stored -> stored.codeHash() == null).orElse(true);
        boolean rotated = refreshHash != null && before.map(StoredHashes::refreshHash)
                .filter(previous -> !previous.equals(refreshHash)).isPresent();

        Map<String, Object> attributes = new HashMap<>(authorization.getAttributes());
        attributes.remove(OAuth2ParameterNames.STATE);
        attributes.remove(LOADED_HASHES);

        MapSqlParameterSource row = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("clientId", UUID.fromString(authorization.getRegisteredClientId()))
                .addValue("userId", UUID.fromString(authorization.getPrincipalName()))
                .addValue("workspaceId", workspaceOf(authorization).orElse(null))
                .addValue("grantType", authorization.getAuthorizationGrantType().getValue())
                .addValue("authorizedScopes", plainJson.writeValueAsString(List.copyOf(authorization.getAuthorizedScopes())))
                .addValue("attributes", write(attributes))
                .addValue("stateHash", state == null ? null : hashOf(state))
                .addValue("consentedAt", consented ? Timestamp.from(now) : null, Types.TIMESTAMP)
                .addValue("lastUsedAt", rotated ? Timestamp.from(now) : null, Types.TIMESTAMP);
        tokenColumns(row, "code", code, codeHash);
        tokenColumns(row, "accessToken", access, access == null ? null : hashOf(access.getToken().getTokenValue()));
        tokenColumns(row, "refreshToken", refresh, refreshHash);
        row.addValue("accessTokenScopes",
                access == null ? null : plainJson.writeValueAsString(List.copyOf(access.getToken().getScopes())));

        jdbc.update("""
                INSERT INTO app_lm_oauth_authorization (
                    id, client_id, user_id, workspace_id, authorization_grant_type, authorized_scopes, attributes,
                    state_hash, code_hash, code_issued_at, code_expires_at, code_metadata,
                    access_token_hash, access_token_issued_at, access_token_expires_at, access_token_scopes,
                    access_token_metadata, refresh_token_hash, refresh_token_issued_at, refresh_token_expires_at,
                    refresh_token_metadata, consented_at, last_used_at)
                VALUES (
                    :id, :clientId, :userId, :workspaceId, :grantType, CAST(:authorizedScopes AS jsonb), :attributes,
                    :stateHash, :codeHash, :codeIssuedAt, :codeExpiresAt, :codeMetadata,
                    :accessTokenHash, :accessTokenIssuedAt, :accessTokenExpiresAt, CAST(:accessTokenScopes AS jsonb),
                    :accessTokenMetadata, :refreshTokenHash, :refreshTokenIssuedAt, :refreshTokenExpiresAt,
                    :refreshTokenMetadata, CAST(:consentedAt AS timestamptz), CAST(:lastUsedAt AS timestamptz))
                ON CONFLICT (id) DO UPDATE SET
                    workspace_id = EXCLUDED.workspace_id,
                    authorized_scopes = EXCLUDED.authorized_scopes,
                    attributes = EXCLUDED.attributes,
                    state_hash = EXCLUDED.state_hash,
                    code_hash = EXCLUDED.code_hash,
                    code_issued_at = EXCLUDED.code_issued_at,
                    code_expires_at = EXCLUDED.code_expires_at,
                    code_metadata = EXCLUDED.code_metadata,
                    access_token_hash = EXCLUDED.access_token_hash,
                    access_token_issued_at = EXCLUDED.access_token_issued_at,
                    access_token_expires_at = EXCLUDED.access_token_expires_at,
                    access_token_scopes = EXCLUDED.access_token_scopes,
                    access_token_metadata = EXCLUDED.access_token_metadata,
                    refresh_token_hash = EXCLUDED.refresh_token_hash,
                    refresh_token_issued_at = EXCLUDED.refresh_token_issued_at,
                    refresh_token_expires_at = EXCLUDED.refresh_token_expires_at,
                    refresh_token_metadata = EXCLUDED.refresh_token_metadata,
                    consented_at = COALESCE(app_lm_oauth_authorization.consented_at, EXCLUDED.consented_at),
                    last_used_at = COALESCE(EXCLUDED.last_used_at, app_lm_oauth_authorization.last_used_at)
                """, row);

        if (rotated) {
            jdbc.update("""
                    INSERT INTO app_lm_oauth_retired_refresh_token (token_hash, authorization_id, retired_at)
                    VALUES (:hash, :id, :now) ON CONFLICT (token_hash) DO NOTHING
                    """, new MapSqlParameterSource("hash", before.get().refreshHash())
                    .addValue("id", id).addValue("now", Timestamp.from(now)));
            record(WorkspaceEventType.OAUTH_TOKEN_REFRESHED, authorization, builder -> { });
        }
        if (consented) {
            record(WorkspaceEventType.OAUTH_GRANT_CREATED, authorization,
                    builder -> builder.detail("scopes", List.copyOf(authorization.getAuthorizedScopes())));
        }
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        jdbc.update("DELETE FROM app_lm_oauth_authorization WHERE id = :id",
                new MapSqlParameterSource("id", UUID.fromString(authorization.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findById(String id) {
        return queryOne("id = :value", UUID.fromString(id));
    }

    /**
     * Its own transaction, committed before the framework turns a miss into {@code invalid_grant}: the revocation of a
     * replayed refresh token must not roll back with the refusal it causes.
     */
    @Override
    @Transactional
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String hash = Tokens.hash(token);
        OAuth2Authorization found = queryOne(predicateFor(tokenType), hash);
        if (found == null && (tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType))) {
            revokeOnReplay(hash);
        }
        return found;
    }

    private void revokeOnReplay(String retiredHash) {
        List<UUID> replayed = jdbc.queryForList("""
                SELECT authorization_id FROM app_lm_oauth_retired_refresh_token WHERE token_hash = :hash
                """, new MapSqlParameterSource("hash", retiredHash), UUID.class);
        for (UUID authorizationId : replayed) {
            OAuth2Authorization grant = queryOne("id = :value", authorizationId);
            if (grant == null) {
                continue;
            }
            log.warn("Retired refresh token replayed for OAuth grant {}; revoking it", authorizationId);
            remove(grant);
            record(WorkspaceEventType.OAUTH_GRANT_REVOKED, grant,
                    builder -> builder.reason(OAuthGrantRevokeReason.REFRESH_REUSE.name()));
        }
    }

    private static String predicateFor(OAuth2TokenType tokenType) {
        if (tokenType == null) {
            return "(state_hash = :value OR code_hash = :value OR access_token_hash = :value OR refresh_token_hash = :value)";
        }
        if (STATE.equals(tokenType)) {
            return "state_hash = :value";
        }
        if (CODE.equals(tokenType)) {
            return "code_hash = :value";
        }
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return "access_token_hash = :value";
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return "refresh_token_hash = :value";
        }
        // Device codes, user codes, ID tokens: nothing this server issues.
        return "false";
    }

    private OAuth2Authorization queryOne(String predicate, Object value) {
        List<OAuth2Authorization> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM app_lm_oauth_authorization WHERE " + predicate,
                new MapSqlParameterSource("value", value), (rs, rowNum) -> toAuthorization(rs));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Compare-and-swap under the row lock {@link #storedHashes} takes. Two requests redeeming one refresh token or one
     * code both read the grant before either saves; the second to reach the lock finds the row moved on (or gone, if
     * the first revoked it) and is refused, so one token is never redeemed twice and a revoked grant never returns.
     */
    private static void refuseIfChangedSinceRead(OAuth2Authorization authorization, Optional<StoredHashes> before) {
        // A replayed code invalidates the grant's tokens through this same save; if a refresh rotates the row first,
        // that invalidation is refused too. The replay itself is still refused, so the narrow loss is tolerated.
        String loaded = authorization.getAttribute(LOADED_HASHES);
        if (loaded == null) {
            return;
        }
        if (before.isEmpty() || !loaded.equals(before.get().fingerprint())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "The grant changed while this request was being served", null));
        }
    }

    private Optional<StoredHashes> storedHashes(UUID id) {
        return jdbc.query("""
                        SELECT state_hash, code_hash, access_token_hash, refresh_token_hash
                        FROM app_lm_oauth_authorization WHERE id = :id FOR UPDATE
                        """, new MapSqlParameterSource("id", id),
                (rs, rowNum) -> StoredHashes.of(rs))
                .stream().findFirst();
    }

    private OAuth2Authorization toAuthorization(ResultSet rs) throws SQLException {
        String clientId = rs.getString("client_id");
        RegisteredClient client = clients.findById(clientId);
        if (client == null) {
            return null;
        }
        Map<String, Object> attributes = read(rs.getString("attributes"));
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(rs.getString("id"))
                .principalName(rs.getString("user_id"))
                .authorizationGrantType(new AuthorizationGrantType(rs.getString("authorization_grant_type")))
                .authorizedScopes(Set.copyOf(readList(rs.getString("authorized_scopes"))))
                .attributes(map -> map.putAll(attributes))
                .attribute(LOADED_HASHES, StoredHashes.of(rs).fingerprint());

        String stateHash = rs.getString("state_hash");
        if (stateHash != null) {
            builder.attribute(OAuth2ParameterNames.STATE, HASH_MARKER + stateHash);
        }
        String codeHash = rs.getString("code_hash");
        if (codeHash != null) {
            builder.token(new OAuth2AuthorizationCode(HASH_MARKER + codeHash,
                    instant(rs, "code_issued_at"), instant(rs, "code_expires_at")),
                    metadata -> metadata.putAll(read(rs, "code_metadata")));
        }
        String accessHash = rs.getString("access_token_hash");
        if (accessHash != null) {
            builder.token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, HASH_MARKER + accessHash,
                    instant(rs, "access_token_issued_at"), instant(rs, "access_token_expires_at"),
                    Set.copyOf(readList(rs.getString("access_token_scopes")))),
                    metadata -> metadata.putAll(read(rs, "access_token_metadata")));
        }
        String refreshHash = rs.getString("refresh_token_hash");
        if (refreshHash != null) {
            builder.token(new OAuth2RefreshToken(HASH_MARKER + refreshHash,
                    instant(rs, "refresh_token_issued_at"), instant(rs, "refresh_token_expires_at")),
                    metadata -> metadata.putAll(read(rs, "refresh_token_metadata")));
        }
        return builder.build();
    }

    private void tokenColumns(MapSqlParameterSource row, String prefix,
                              OAuth2Authorization.Token<? extends OAuth2Token> token, String hash) {
        row.addValue(prefix + "Hash", hash)
                .addValue(prefix + "IssuedAt", token == null ? null : timestamp(token.getToken().getIssuedAt()))
                .addValue(prefix + "ExpiresAt", token == null ? null : timestamp(token.getToken().getExpiresAt()))
                .addValue(prefix + "Metadata", token == null ? null : write(token.getMetadata()));
    }

    private void record(WorkspaceEventType type, OAuth2Authorization authorization,
                        Consumer<AuditService.Builder> details) {
        AuditService.Builder event = audit.event(type)
                .actor(UUID.fromString(authorization.getPrincipalName()))
                .workspace(workspaceOf(authorization).orElse(null))
                .target(GRANT_TARGET, authorization.getId())
                .detail("clientId", clientIdOf(authorization))
                .from(currentRequest());
        details.accept(event);
        event.record();
    }

    private String clientIdOf(OAuth2Authorization authorization) {
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        return client == null ? authorization.getRegisteredClientId() : client.getClientId();
    }

    /** The workspace the consent screen chose, carried on the stored authorization request. */
    public static Optional<UUID> workspaceOf(OAuth2Authorization authorization) {
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (request == null) {
            return Optional.empty();
        }
        return parseUuid(request.getAdditionalParameters().get(WORKSPACE_PARAMETER));
    }

    static Optional<UUID> parseUuid(Object value) {
        if (!(value instanceof String text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private static String hashOf(String value) {
        return value.startsWith(HASH_MARKER) ? value.substring(HASH_MARKER.length()) : Tokens.hash(value);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }

    private Map<String, Object> read(String value) {
        return value == null || value.isBlank() ? Map.of() : json.readValue(value, MAP);
    }

    private Map<String, Object> read(ResultSet rs, String column) {
        try {
            return read(rs.getString(column));
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read " + column, ex);
        }
    }

    private Collection<String> readList(String value) {
        return value == null ? List.of() : plainJson.readValue(value, new TypeReference<List<String>>() {});
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record StoredHashes(String stateHash, String codeHash, String accessHash, String refreshHash) {

        static StoredHashes of(ResultSet rs) throws SQLException {
            return new StoredHashes(rs.getString("state_hash"), rs.getString("code_hash"),
                    rs.getString("access_token_hash"), rs.getString("refresh_token_hash"));
        }

        String fingerprint() {
            return String.join("|", Objects.toString(stateHash, ""), Objects.toString(codeHash, ""),
                    Objects.toString(accessHash, ""), Objects.toString(refreshHash, ""));
        }
    }
}
