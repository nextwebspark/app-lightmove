package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.position.constant.PositionDiscipline;
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
 * <p><b>Two owners, one table.</b> A null {@link #workspaceId} is a LightMove library template every
 * workspace can read and none can edit; a non-null one belongs to that workspace alone. Nothing writes
 * the second kind yet — the library is seeded by V42 and managed as migrations — but every read is
 * already scoped, so the template-management screen is a write path rather than a re-modelling.
 *
 * <p>The content is a jsonb document ({@link PositionTemplateBody}) and the match keywords are a child
 * table, and the split is deliberate: the body is read whole and never queried, while the keywords are
 * the catalog's lookup key. V42's header carries the full argument.
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

    /** True for a LightMove library template — readable by every workspace, owned by none. */
    public boolean isSharedLibrary() {
        return workspaceId == null;
    }

    /**
     * Whether a mandate's role title lands on this template: a case-insensitive substring match on any
     * keyword. A template with no keywords never matches — the generic fallback is reached by code,
     * not by matching a title nothing else recognised.
     */
    public boolean matchesTitle(String roleTitle) {
        if (roleTitle == null) {
            return false;
        }
        String title = roleTitle.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(title::contains);
    }
}
