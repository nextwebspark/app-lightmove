package app.lightmove.api.core.security.avatar;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Account data: every read is by the person or by the token their link carries, never by workspace. */
public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {

    Optional<UserAvatar> findByUserId(UUID userId);

    Optional<UserAvatar> findByToken(String token);
}
