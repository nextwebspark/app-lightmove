package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.enrichment.sourcing.constant.CriteriaSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A position's Find executives search (V213): drafted once from the brief, then edited in place. */
@Entity
@Table(name = "app_lm_executive_sourcing_criteria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourcingCriteria extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria", nullable = false)
    private SearchCriteria criteria;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private CriteriaSource source;

    /** The brief the search was drafted from; null once a person has edited it. */
    @Column(name = "brief_fingerprint", length = 64)
    private String briefFingerprint;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static SourcingCriteria drafted(UUID workspaceId, UUID projectId, SearchCriteria criteria,
                                           CriteriaSource source, String briefFingerprint, UUID userId) {
        SourcingCriteria saved = new SourcingCriteria();
        saved.workspaceId = workspaceId;
        saved.projectId = projectId;
        saved.redraft(criteria, source, briefFingerprint, userId);
        return saved;
    }

    public void redraft(SearchCriteria drafted, CriteriaSource draftedBy, String fingerprint, UUID userId) {
        criteria = drafted;
        source = draftedBy;
        briefFingerprint = fingerprint;
        updatedBy = userId;
    }

    public void edit(SearchCriteria edited, UUID userId) {
        criteria = edited;
        source = CriteriaSource.EDITED;
        updatedBy = userId;
    }
}
