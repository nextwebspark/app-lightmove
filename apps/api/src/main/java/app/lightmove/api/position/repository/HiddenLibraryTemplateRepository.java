package app.lightmove.api.position.repository;

import app.lightmove.api.position.model.HiddenLibraryTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HiddenLibraryTemplateRepository extends JpaRepository<HiddenLibraryTemplate, UUID> {

    List<HiddenLibraryTemplate> findByWorkspaceId(UUID workspaceId);

    /** One statement: a derived delete would load the row first and then remove it. */
    @Modifying
    @Query("delete from HiddenLibraryTemplate hiddenRow where hiddenRow.workspaceId = :workspaceId and hiddenRow.code = :code")
    int deleteByWorkspaceIdAndCode(@Param("workspaceId") UUID workspaceId, @Param("code") String code);
}
