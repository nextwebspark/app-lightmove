package app.lightmove.api.core.storage.repository;

import app.lightmove.api.core.storage.constant.EmailImageKind;
import app.lightmove.api.core.storage.model.EmailImage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailImageRepository extends JpaRepository<EmailImage, UUID> {

    Optional<EmailImage> findByToken(String token);

    Optional<EmailImage> findByKindAndWorkspaceIdAndSourceUrlSha256(EmailImageKind kind, UUID workspaceId,
                                                                    String sourceUrlSha256);
}
