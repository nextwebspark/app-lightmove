package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
 * The clients the authorization server sees: the registered ones, and any client whose id is the https URL of its own
 * metadata document (the MCP spec's preferred way in, which Spring does not offer). Such a document is fetched behind
 * {@link ClientMetadataFetcher}'s guards, and kept as a row — a grant needs a client row to belong to — until the
 * lifetime its host gave it runs out.
 *
 * <p>The fetch runs outside any transaction and the row is written in its own, so a slow host never holds a database
 * connection and a caller's read-only transaction never refuses the write.
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
        Instant now = clock.instant();
        Optional<OAuthClient> cached = clients.findByClientId(clientId)
                .filter(client -> client.getSource() == OAuthClientSource.CIMD);
        if (cached.isPresent() && cached.get().getMetadataExpiresAt().isAfter(now)) {
            return registered.toRegisteredClient(cached.get());
        }
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
                return registered.toRegisteredClient(cached.get());
            }
            log.info("Client metadata document {} refused: {}", clientId, unavailable.getMessage());
            return null;
        }
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
