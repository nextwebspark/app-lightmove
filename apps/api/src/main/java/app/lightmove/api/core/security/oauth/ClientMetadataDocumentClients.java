package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The registered clients, and any client whose id is the https URL of its own metadata document (CIMD, which Spring
 * lacks): fetched behind {@link ClientMetadataFetcher}'s guards and kept as a row, which a grant needs, until it expires.
 *
 * <p>A fetch runs outside any transaction, one at a time per document; a failed one is not retried for
 * {@code cimd-min-ttl}, so a host that is down costs one caller its deadline, not every caller.
 */
@Slf4j
@Primary
@Component
public class ClientMetadataDocumentClients implements RegisteredClientRepository {

    private final OAuthRegisteredClients registered;
    private final OAuthClientJpaRepository clients;
    private final ClientMetadataFetcher fetcher;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;
    private final McpSettings settings;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ConcurrentHashMap<String, CompletableFuture<RegisteredClient>> fetching = new ConcurrentHashMap<>();
    /** Documents refused or unreachable with no copy to fall back on; asked again only once this lapses. */
    private final Cache<String, Boolean> recentlyRefused;

    public ClientMetadataDocumentClients(OAuthRegisteredClients registered, OAuthClientJpaRepository clients,
                                         ClientMetadataFetcher fetcher, NamedParameterJdbcTemplate jdbc,
                                         PlatformTransactionManager transactions, Clock clock,
                                         LightMoveProperties properties) {
        this.registered = registered;
        this.clients = clients;
        this.fetcher = fetcher;
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.settings = properties.mcp();
        this.recentlyRefused = Caffeine.newBuilder()
                .expireAfterWrite(settings.cimdMinTtl())
                .maximumSize(10_000)
                .build();
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        registered.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        return registered.findById(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        if (!ClientMetadataDocument.isDocumentUrl(clientId)) {
            return registered.findByClientId(clientId);
        }
        if (!ClientMetadataDocument.isAcceptableUrl(clientId)) {
            return null;
        }
        Optional<OAuthClient> cached = storedDocumentClient(clientId);
        if (cached.isPresent() && cached.get().getMetadataExpiresAt().isAfter(clock.instant())) {
            return registered.toRegisteredClient(cached.get());
        }
        if (recentlyRefused.getIfPresent(clientId) != null) {
            return null;
        }
        CompletableFuture<RegisteredClient> mine = new CompletableFuture<>();
        CompletableFuture<RegisteredClient> running = fetching.putIfAbsent(clientId, mine);
        if (running != null) {
            return running.join();
        }
        try {
            RegisteredClient client = refresh(clientId, cached);
            mine.complete(client);
            return client;
        } catch (RuntimeException failed) {
            mine.completeExceptionally(failed);
            throw failed;
        } finally {
            fetching.remove(clientId, mine);
        }
    }

    /**
     * Only what is already on file, never a fetch: for revocation, where a client never fetched owns no grant, and a
     * fetch would let anyone make the server request URLs through an endpoint that needs no proof.
     */
    public RegisteredClient findStoredByClientId(String clientId) {
        return registered.findByClientId(clientId);
    }

    private RegisteredClient refresh(String clientId, Optional<OAuthClient> cached) {
        Instant now = clock.instant();
        try {
            FetchedClientMetadata fetched = fetcher.fetch(URI.create(clientId));
            ClientMetadataDocument document = ClientMetadataDocument.read(clientId, fetched.body());
            ownTransaction.executeWithoutResult(status -> store(document, now, now.plus(fetched.lifetime())));
            return registered.findByClientId(clientId);
        } catch (ClientMetadataUnavailable unavailable) {
            if (unavailable.refusal().isOutage() && cached.isPresent()
                    && cached.get().getMetadataFetchedAt().plus(settings.cimdStaleIfError()).isAfter(now)) {
                log.warn("Client metadata document {} unavailable ({}); serving the copy fetched at {}", clientId,
                        unavailable.getMessage(), cached.get().getMetadataFetchedAt());
                ownTransaction.executeWithoutResult(status -> retryNoSoonerThan(clientId, now.plus(settings.cimdMinTtl())));
                return registered.toRegisteredClient(cached.get());
            }
            log.info("Client metadata document {} refused: {}", clientId, unavailable.getMessage());
            recentlyRefused.put(clientId, Boolean.TRUE);
            return null;
        }
    }

    private Optional<OAuthClient> storedDocumentClient(String clientId) {
        return clients.findByClientId(clientId).filter(client -> client.getSource() == OAuthClientSource.CIMD);
    }

    /** The stale copy serves until then without anyone waiting on the host again; its fetch time is left as it was. */
    private void retryNoSoonerThan(String clientId, Instant retryAt) {
        jdbc.update("""
                UPDATE app_lm_oauth_client SET metadata_expires_at = :retryAt
                WHERE client_id = :clientId AND source = 'CIMD'
                """, new MapSqlParameterSource("clientId", clientId).addValue("retryAt", Timestamp.from(retryAt)));
    }

    /** One row per document URL; a registered client that happens to share the id is never overwritten. */
    private void store(ClientMetadataDocument document, Instant fetchedAt, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO app_lm_oauth_client (client_id, client_name, client_uri, logo_uri, redirect_uris, scopes,
                                                 source, metadata_fetched_at, metadata_expires_at)
                VALUES (:clientId, :clientName, :clientUri, :logoUri, CAST(:redirectUris AS jsonb),
                        CAST(:scopes AS jsonb), 'CIMD', :fetchedAt, :expiresAt)
                ON CONFLICT (client_id) DO UPDATE SET
                    client_name = EXCLUDED.client_name,
                    client_uri = EXCLUDED.client_uri,
                    logo_uri = EXCLUDED.logo_uri,
                    redirect_uris = EXCLUDED.redirect_uris,
                    scopes = EXCLUDED.scopes,
                    metadata_fetched_at = EXCLUDED.metadata_fetched_at,
                    metadata_expires_at = EXCLUDED.metadata_expires_at,
                    version = app_lm_oauth_client.version + 1
                WHERE app_lm_oauth_client.source = 'CIMD'
                """, new MapSqlParameterSource()
                .addValue("clientId", document.clientId())
                .addValue("clientName", document.clientName())
                .addValue("clientUri", document.clientUri())
                .addValue("logoUri", document.logoUri())
                .addValue("redirectUris", json.writeValueAsString(document.redirectUris()))
                .addValue("scopes", json.writeValueAsString(document.scopes()))
                .addValue("fetchedAt", Timestamp.from(fetchedAt))
                .addValue("expiresAt", Timestamp.from(expiresAt)));
    }
}
