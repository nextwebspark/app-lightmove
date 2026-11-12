package app.lightmove.api.core.security.avatar;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Account data: every read is by the person or by the token their link carries, never by workspace. */
public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {

    Optional<UserAvatar> findByUserId(UUID userId);

    Optional<UserAvatar> findByToken(String token);

    /** The photo's hash without its bytes, for a caller that needs the bytes only when it has no copy yet. */
    @Query("select avatar.sha256 from UserAvatar avatar where avatar.userId = :userId")
    Optional<String> findSha256ByUserId(@Param("userId") UUID userId);
}
