package app.lightmove.api.position.repository;

import app.lightmove.api.position.model.PositionTemplate;
import app.lightmove.api.position.model.TemplateCodeCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PositionTemplateRepository extends JpaRepository<PositionTemplate, UUID> {

    /**
     * Every template one workspace can use: its own rows, plus each active library row it has neither
     * copied nor hidden — its own first.
     *
     * <p>The ordering is the tenant rule made visible: a firm's own version of a role is the one its
     * picker leads with and the one a title matches against first. A firm's copy shadows the library
     * row by code, so the two never both reach the picker.
     *
     * <p>The keywords are fetched with the templates rather than left lazy — matching a title touches
     * each candidate's keywords in turn, so a lazy collection costs one round trip per template ruled
     * out, on the path every new project takes. Hibernate 6 de-duplicates the fetched parents itself
     * and the index column survives the join, so no {@code distinct} is needed.
     */
    @Query("""
            select template from PositionTemplate template
            left join fetch template.keywords
            where template.active = true
              and (template.workspaceId = :workspaceId
                   or (template.workspaceId is null
                       and not exists (select own.id from PositionTemplate own
                                       where own.workspaceId = :workspaceId and own.code = template.code)
                       and not exists (select hiddenRow.id from HiddenLibraryTemplate hiddenRow
                                       where hiddenRow.workspaceId = :workspaceId and hiddenRow.code = template.code)))
            order by case when template.workspaceId is null then 1 else 0 end,
                     template.sortOrder, template.title
            """)
    List<PositionTemplate> findAllVisibleTo(@Param("workspaceId") UUID workspaceId);

    /**
     * One template, if this workspace may use it — the same rule as {@link #findAllVisibleTo}. A
     * template id is a request parameter: another firm's template, or a library template this firm has
     * replaced or hidden, must 404 rather than seed a brief.
     */
    @Query("""
            select template from PositionTemplate template
            where template.id = :templateId
              and template.active = true
              and (template.workspaceId = :workspaceId
                   or (template.workspaceId is null
                       and not exists (select own.id from PositionTemplate own
                                       where own.workspaceId = :workspaceId and own.code = template.code)
                       and not exists (select hiddenRow.id from HiddenLibraryTemplate hiddenRow
                                       where hiddenRow.workspaceId = :workspaceId and hiddenRow.code = template.code)))
            """)
    Optional<PositionTemplate> findVisibleTo(@Param("templateId") UUID templateId,
                                             @Param("workspaceId") UUID workspaceId);

    /** The whole library, archived templates included. */
    @Query("""
            select template from PositionTemplate template
            left join fetch template.keywords
            where template.workspaceId is null
            order by template.sortOrder, template.title
            """)
    List<PositionTemplate> findLibrary();

    @Query("""
            select template from PositionTemplate template
            left join fetch template.keywords
            where template.workspaceId = :workspaceId
            order by template.sortOrder, template.title
            """)
    List<PositionTemplate> findOwnedBy(@Param("workspaceId") UUID workspaceId);

    @Query("""
            select template from PositionTemplate template
            left join fetch template.keywords
            where template.workspaceId is null and template.code = :code
            """)
    Optional<PositionTemplate> findLibraryTemplate(@Param("code") String code);

    @Query("""
            select template from PositionTemplate template
            left join fetch template.keywords
            where template.workspaceId = :workspaceId and template.code = :code
            """)
    Optional<PositionTemplate> findWorkspaceTemplate(@Param("workspaceId") UUID workspaceId,
                                                     @Param("code") String code);

    /** How many firms hold a template under this code, and so will not see an edit to the library's. */
    @Query("""
            select count(template) from PositionTemplate template
            where template.workspaceId is not null and template.code = :code
            """)
    long countWorkspaceTemplatesCoded(@Param("code") String code);

    @Query("""
            select new app.lightmove.api.position.model.TemplateCodeCount(template.code, count(template))
            from PositionTemplate template
            where template.workspaceId is not null
            group by template.code
            """)
    List<TemplateCodeCount> countWorkspaceTemplatesByCode();

    @Query("select coalesce(max(template.sortOrder), 0) from PositionTemplate template where template.workspaceId is null")
    int findLastLibrarySortOrder();
}
