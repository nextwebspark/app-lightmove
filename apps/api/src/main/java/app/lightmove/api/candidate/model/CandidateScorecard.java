package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ScorecardReportSection;
import app.lightmove.api.candidate.constant.ScorecardStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A consultant's assessment of one mapped executive against the position's brief (V135): a score per competency
 * and a written report, either of which the AI may have drafted (V136). Staff-only — it ranks a person — so it
 * never rides {@code CandidateResponse}, which a client seat reads.
 */
@Entity
@Table(name = "app_lm_candidate_scorecard")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateScorecard extends BaseEntity {

    /** The unique constraint two first saves of one assessment race on. */
    public static final String ONE_PER_MAPPING = "app_lm_candidate_scorecard_candidate_uk";

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 10;
    public static final int MAX_EVIDENCE = 2000;
    public static final int MAX_REPORT_SECTION = 4000;

    private static final String REPORT_KEY_PREFIX = "report:";

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ScorecardStatus status = ScorecardStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", nullable = false)
    private List<CriterionScore> scores = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", nullable = false)
    private ScorecardReport report = ScorecardReport.empty();

    /** The criteria and report sections still holding the AI's draft untouched: {@link #draftKeyOf} keys. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_drafted", nullable = false)
    private Set<String> aiDrafted = new HashSet<>();

    @Column(name = "ai_drafted_at")
    private Instant aiDraftedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public static CandidateScorecard startFor(Candidate candidate, UUID workspaceId) {
        CandidateScorecard scorecard = new CandidateScorecard();
        scorecard.workspaceId = workspaceId;
        scorecard.projectId = candidate.getProjectId();
        scorecard.candidateId = candidate.getId();
        return scorecard;
    }

    public static String draftKeyOf(ScorecardReportSection section) {
        return REPORT_KEY_PREFIX + section.value();
    }

    /**
     * Writes what was sent over what was held. Marking it reviewed stamps who and when and adopts every AI value as
     * the reviewer's own, so no later run rewrites a reviewed judgement; any other save is a draft again, and an AI
     * value it changed is the consultant's from then on.
     */
    public void record(List<CriterionScore> newScores, ScorecardReport newReport, boolean markReviewed, UUID by,
                       Instant at) {
        this.aiDrafted = markReviewed ? new HashSet<>() : stillDrafted(newScores, newReport);
        this.scores = new ArrayList<>(newScores);
        this.report = newReport;
        this.updatedBy = by;
        if (markReviewed) {
            this.status = ScorecardStatus.REVIEWED;
            this.reviewedBy = by;
            this.reviewedAt = at;
        } else {
            reopen();
        }
    }

    /**
     * Writes the AI's draft wherever no consultant holds a judgement: an empty criterion or section, or one still
     * holding an earlier untouched draft. Answers whether anything on file changed; a change makes it a draft again.
     */
    public boolean applyAiDraft(List<CriterionScore> draftedScores, ScorecardReport draftedReport, Instant at) {
        Set<String> drafted = new HashSet<>(aiDrafted);
        boolean scoresChanged = takeDraftedScores(draftedScores, drafted);
        boolean reportChanged = takeDraftedReport(draftedReport, drafted);
        boolean changed = scoresChanged || reportChanged;
        this.aiDrafted = drafted;
        this.aiDraftedAt = at;
        if (changed) {
            reopen();
        }
        return changed;
    }

    /** Whether a draft could still write anything: a criterion or section nobody holds, or one still the AI's. */
    public boolean hasOpenAmong(Collection<String> criterionKeys) {
        Set<String> held = byKey(scores).keySet();
        boolean criterionOpen = criterionKeys.stream().anyMatch(key -> !held.contains(key) || aiDrafted.contains(key));
        boolean sectionOpen = Arrays.stream(ScorecardReportSection.values())
                .anyMatch(section -> report.textOf(section) == null || aiDrafted.contains(draftKeyOf(section)));
        return criterionOpen || sectionOpen;
    }

    private boolean takeDraftedScores(List<CriterionScore> draftedScores, Set<String> drafted) {
        Map<String, CriterionScore> merged = byKey(scores);
        boolean changed = false;
        for (CriterionScore proposed : draftedScores) {
            String key = proposed.matchKey();
            CriterionScore held = merged.get(key);
            if (proposed.holdsNothing() || (held != null && !drafted.contains(key))) {
                continue;
            }
            changed |= !proposed.sameJudgementAs(held);
            merged.put(key, proposed);
            drafted.add(key);
        }
        this.scores = new ArrayList<>(merged.values());
        return changed;
    }

    private boolean takeDraftedReport(ScorecardReport draftedReport, Set<String> drafted) {
        ScorecardReport nextReport = report;
        boolean changed = false;
        for (ScorecardReportSection section : ScorecardReportSection.values()) {
            String proposed = draftedReport.textOf(section);
            String held = report.textOf(section);
            String key = draftKeyOf(section);
            if (proposed == null || (held != null && !drafted.contains(key))) {
                continue;
            }
            changed |= !proposed.equals(held);
            nextReport = nextReport.withText(section, proposed);
            drafted.add(key);
        }
        this.report = nextReport;
        return changed;
    }

    private void reopen() {
        this.status = ScorecardStatus.DRAFT;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }

    private Set<String> stillDrafted(List<CriterionScore> newScores, ScorecardReport newReport) {
        Map<String, CriterionScore> held = byKey(scores);
        Map<String, CriterionScore> sent = byKey(newScores);
        Set<String> kept = new HashSet<>();
        for (CriterionScore score : held.values()) {
            String key = score.matchKey();
            if (aiDrafted.contains(key) && score.sameJudgementAs(sent.get(key))) {
                kept.add(key);
            }
        }
        for (ScorecardReportSection section : ScorecardReportSection.values()) {
            String key = draftKeyOf(section);
            if (aiDrafted.contains(key) && Objects.equals(report.textOf(section), newReport.textOf(section))) {
                kept.add(key);
            }
        }
        return kept;
    }

    private static Map<String, CriterionScore> byKey(List<CriterionScore> scores) {
        Map<String, CriterionScore> byKey = new LinkedHashMap<>();
        scores.forEach(score -> byKey.putIfAbsent(score.matchKey(), score));
        return byKey;
    }
}
