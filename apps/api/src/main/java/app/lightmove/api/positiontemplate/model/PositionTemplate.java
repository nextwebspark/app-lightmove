package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One role template: the brief a mandate for this kind of role starts from.
 *
 * <p><b>Two owners, one table.</b> A null {@link #workspaceId} is a LightMove library template, edited
 * by a platform super admin; a non-null one belongs to that workspace alone. A workspace row sharing a
 * library row's {@link #code} is the firm's copy of it and shadows it in every read (V52).
 *
 * <p>The content is a jsonb document ({@link PositionTemplateBody}) read whole and never queried,
 * while the match keywords are a child table because they are the catalog's lookup key.
 */
@Entity
@Table(name = "app_lm_position_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionTemplate extends BaseEntity {

    /** Null for a shared library template; a workspace's id for one that firm owns. */
    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", nullable = false, length = 32)
    private PositionDiscipline discipline;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority", nullable = false, length = 16)
    private Seniority seniority;

    @Column(name = "summary", length = 300)
    private String summary;

    /** Display order in the picker, and the order titles are matched against. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body", nullable = false)
    private PositionTemplateBody body = PositionTemplateBody.empty();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_template_keyword",
            joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "keyword", nullable = false, length = 80)
    private List<String> keywords = new ArrayList<>();

    /** Moves when the content is saved — not on archiving, which {@code updated_at} records too. */
    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt = now();

    @Column(name = "revised_by")
    private UUID revisedBy;

    /** On a workspace copy of a library template: the library's {@link #revisedAt} when it was taken. */
    @Column(name = "customised_from")
    private Instant customisedFrom;

    public static PositionTemplate forLibrary(String code, PositionTemplateDraft draft, int sortOrder,
                                              UUID editorId) {
        return create(null, code, draft, sortOrder, editorId);
    }

    public static PositionTemplate ownedBy(UUID workspaceId, String code, PositionTemplateDraft draft,
                                           UUID editorId) {
        return create(workspaceId, code, draft, 0, editorId);
    }

    /** A firm's copy of a library template, which shadows it from now on under the same code. */
    public static PositionTemplate customisationOf(PositionTemplate library, UUID workspaceId,
                                                   PositionTemplateDraft draft, UUID editorId) {
        PositionTemplate copy = create(workspaceId, library.code, draft, library.sortOrder, editorId);
        copy.customisedFrom = library.revisedAt;
        return copy;
    }

    private static PositionTemplate create(UUID workspaceId, String code, PositionTemplateDraft draft,
                                           int sortOrder, UUID editorId) {
        PositionTemplate template = new PositionTemplate();
        template.workspaceId = workspaceId;
        template.code = code;
        template.sortOrder = sortOrder;
        template.revise(draft, editorId);
        return template;
    }

    /** Takes an already-validated, normalised draft. */
    public void revise(PositionTemplateDraft draft, UUID editorId) {
        title = draft.title();
        discipline = draft.discipline();
        seniority = draft.seniority();
        summary = draft.summary();
        body = draft.body();
        keywords.clear();
        keywords.addAll(draft.keywords());
        revisedAt = now();
        revisedBy = editorId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public PositionTemplateDraft toDraft() {
        return new PositionTemplateDraft(title, discipline, seniority, summary, List.copyOf(keywords), body)
                .normalised();
    }

    /** True for a LightMove library template — readable by every workspace, owned by none. */
    public boolean isSharedLibrary() {
        return workspaceId == null;
    }

    /** A firm's copy of a library template, as opposed to one the firm wrote itself. */
    public boolean isCustomisation() {
        return workspaceId != null && customisedFrom != null;
    }

    /** Whether the library template this copy was taken from has been revised since. */
    public boolean isBehind(PositionTemplate library) {
        return customisedFrom != null && library.revisedAt.isAfter(customisedFrom);
    }

    /**
     * Whether a mandate's role title lands on this template: a case-insensitive substring match on
     * any keyword. A template with no keywords never matches — the generic fallback is reached by
     * code.
     *
     * <p>Both sides are lower-cased, not just the title: a row written before the editor normalised
     * keywords, or straight into the table, would otherwise stop matching silently.
     */
    public boolean matchesTitle(String roleTitle) {
        if (roleTitle == null) {
            return false;
        }
        String title = roleTitle.toLowerCase(Locale.ROOT);
        return keywords.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT).trim())
                .anyMatch(title::contains);
    }

    // Postgres keeps microseconds. An in-memory nanosecond instant would compare after its own stored copy.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
