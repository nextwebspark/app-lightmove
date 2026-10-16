package app.uncava.api.core.security.repository;

import app.uncava.api.core.security.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Case-insensitive by virtue of the citext column — no LOWER() needed, and none should be added. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
