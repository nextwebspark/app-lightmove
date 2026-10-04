package app.lightmove.api.positiontemplate.repository;

import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.TemplateCodeCount;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PositionTemplateRepository extends JpaRepository<PositionTemplate, UUID> {

    /**
     * The workspace's own rows first, then each active library row it has neither copied nor hidden.
     * Keywords are fetched eagerly: title matching touches each in turn, on every project creation.
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
     * The rule of {@link #findAllVisibleTo}: a template id is a request parameter, so another firm's, or
     * a library template this firm replaced or hid, must 404.
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

    @Query("select template.code from PositionTemplate template where template.workspaceId is null")
    Set<String> findLibraryCodes();

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
            select new app.lightmove.api.positiontemplate.model.TemplateCodeCount(template.code, count(template))
            from PositionTemplate template
            where template.workspaceId is not null
            group by template.code
            """)
    List<TemplateCodeCount> countWorkspaceTemplatesByCode();

    @Query("select coalesce(max(template.sortOrder), 0) from PositionTemplate template where template.workspaceId is null")
    int findLastLibrarySortOrder();
}
