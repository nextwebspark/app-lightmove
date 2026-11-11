package app.lightmove.api.core.security.repository;

import app.lightmove.api.core.security.model.UserIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<UserIdentity> findByUserIdOrderByLinkedAtAsc(UUID userId);
}
