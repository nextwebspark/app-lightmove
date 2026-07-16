package app.lightmove.api.core.security.repository;

import app.lightmove.api.core.security.constant.AuthProvider;
import app.lightmove.api.core.security.model.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
