package app.lightmove.api.core.security.oauth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Clients are not tenant data: one registration serves every workspace that connects it. */
public interface OAuthClientJpaRepository extends JpaRepository<OAuthClient, UUID> {

    Optional<OAuthClient> findByClientId(String clientId);
}
