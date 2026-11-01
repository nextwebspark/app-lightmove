package app.lightmove.api.core.security.repository;

import app.lightmove.api.core.security.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Case-insensitive by virtue of the citext column — no LOWER() needed, and none should be added. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Password login's read: attempts on one account wait their turn, or concurrent ones lost each other's failure
     * count and answered 409 — which only an existing account could, so the conflict told guessers it existed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForSignIn(String email);

    /** Signup's organisation step: submits for one account wait their turn, so the membership check sees the first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockToFoundFirstWorkspace(UUID id);
}
