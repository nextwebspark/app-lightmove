package app.lightmove.api.position.repository;

import app.lightmove.api.position.model.PositionTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PositionTemplateRepository extends JpaRepository<PositionTemplate, UUID> {

    /**
     * Every template one workspace can see: the shared library plus its own, its own first.
     *
     * <p>The ordering is the tenant rule made visible — where a firm has written its own version of a
     * role, that is the one its picker leads with and the one a new mandate's title matches against
     * before the library's.
     *
     * <p>The keywords are fetched with the templates rather than left lazy. Matching a title walks
     * the catalog until something hits, touching each candidate's keywords in turn — so a lazy
     * collection makes creating a mandate cost one extra round trip per template it had to rule out,
     * on the path every new project takes, worst on the titles that match nothing. Hibernate 6
     * de-duplicates the fetched parents itself, and the collection's index column survives the join,
     * so no {@code distinct} is needed and the ordering above still holds.
     */
    @Query("""
            select template from PositionTemplate template
            left join fetch template.keywords
            where template.active = true
              and (template.workspaceId is null or template.workspaceId = :workspaceId)
            order by case when template.workspaceId is null then 1 else 0 end,
                     template.sortOrder, template.title
            """)
    List<PositionTemplate> findAllVisibleTo(@Param("workspaceId") UUID workspaceId);

    /**
     * One template, if this workspace may see it. Scoped rather than a plain {@code findById}: a
     * template id is a request parameter, and another firm's template must 404 rather than seed a
     * brief with content it was never shown.
     */
    @Query("""
            select template from PositionTemplate template
            where template.id = :templateId
              and template.active = true
              and (template.workspaceId is null or template.workspaceId = :workspaceId)
            """)
    Optional<PositionTemplate> findVisibleTo(@Param("templateId") UUID templateId,
                                             @Param("workspaceId") UUID workspaceId);
}
