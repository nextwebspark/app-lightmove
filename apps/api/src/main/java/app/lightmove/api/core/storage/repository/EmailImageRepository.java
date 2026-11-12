package app.lightmove.api.core.storage.repository;

import app.lightmove.api.core.storage.constant.EmailImageKind;
import app.lightmove.api.core.storage.model.EmailImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailImageRepository extends JpaRepository<EmailImage, UUID> {

    Optional<EmailImage> findByToken(String token);

    Optional<EmailImage> findByKindAndWorkspaceIdAndSourceSha256(EmailImageKind kind, UUID workspaceId,
                                                                 String sourceSha256);

    Optional<EmailImage> findByKindAndUserIdAndSourceSha256(EmailImageKind kind, UUID userId, String sourceSha256);

    Optional<EmailImage> findByKindAndWorkspaceIdAndUserIdAndSourceSha256(EmailImageKind kind, UUID workspaceId,
                                                                          UUID userId, String sourceSha256);

    List<EmailImage> findByTokenIn(Collection<String> tokens);
}
