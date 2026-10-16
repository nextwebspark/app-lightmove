package app.uncava.api.core.security.repository;

import app.uncava.api.core.security.model.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
}
