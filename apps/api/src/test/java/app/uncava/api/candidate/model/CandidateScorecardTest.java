package app.uncava.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.uncava.api.candidate.constant.CandidateSource;
import app.uncava.api.candidate.constant.ScorecardReportSection;
import app.uncava.api.candidate.constant.ScorecardStatus;
import app.uncava.api.common.constant.CompetencyPanel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateScorecardTest {

    private static final UUID CONSULTANT = UUID.randomUUID();

    @Test
    @DisplayName("the AI fills what nobody scored and leaves a consultant's score and words alone")
    void theAiNeverOverwritesAConsultant() {
        CandidateScorecard scorecard = scorecard();
        scorecard.record(List.of(capitalMarkets(7, "Ran the 2019 listing.")),
                report("Their own summary.", null), false, CONSULTANT, Instant.now());

        boolean changed = scorecard.applyAiDraft(List.of(capitalMarkets(3, "AI view."), treasury(6, "Group treasury.")),
                report("AI summary.", "AI concerns."), Instant.now());

        assertThat(changed).isTrue();
        assertThat(scorecard.getScores()).containsExactly(capitalMarkets(7, "Ran the 2019 listing."),
                treasury(6, "Group treasury."));
        assertThat(scorecard.getReport().careerSummary()).isEqualTo("Their own summary.");
        assertThat(scorecard.getReport().concerns()).isEqualTo("AI concerns.");
        assertThat(scorecard.getAiDrafted()).containsExactlyInAnyOrder(treasury(0, null).matchKey(),
                CandidateScorecard.draftKeyOf(ScorecardReportSection.CONCERNS));
    }

    @Test
    @DisplayName("a re-run rewrites the AI's own untouched draft, but a value a consultant changed is theirs")
    void anEditMakesAnAiValueTheConsultants() {
        CandidateScorecard scorecard = scorecard();
        scorecard.applyAiDraft(List.of(capitalMarkets(5, "First read."), treasury(4, "Thin.")),
                ScorecardReport.empty(), Instant.now());
        scorecard.record(List.of(capitalMarkets(5, "First read."), treasury(6, "Confirmed by a referee.")),
                ScorecardReport.empty(), false, CONSULTANT, Instant.now());

        scorecard.applyAiDraft(List.of(capitalMarkets(8, "Second read."), treasury(2, "Second read.")),
                ScorecardReport.empty(), Instant.now());

        assertThat(scorecard.getScores()).containsExactly(capitalMarkets(8, "Second read."),
                treasury(6, "Confirmed by a referee."));
        assertThat(scorecard.getAiDrafted()).containsExactly(capitalMarkets(0, null).matchKey());
    }

    @Test
    @DisplayName("marking it reviewed adopts every AI value, so a later run changes nothing and it stays reviewed")
    void aReviewAdoptsTheDraft() {
        CandidateScorecard scorecard = scorecard();
        scorecard.applyAiDraft(List.of(capitalMarkets(5, "AI read.")), report("AI summary.", null), Instant.now());
        scorecard.record(List.of(capitalMarkets(5, "AI read.")), report("AI summary.", null), true, CONSULTANT,
                Instant.now());

        boolean changed = scorecard.applyAiDraft(List.of(capitalMarkets(9, "Another read.")),
                report("Another summary.", null), Instant.now());

        assertThat(changed).isFalse();
        assertThat(scorecard.getAiDrafted()).isEmpty();
        assertThat(scorecard.getStatus()).isEqualTo(ScorecardStatus.REVIEWED);
        assertThat(scorecard.getScores()).containsExactly(capitalMarkets(5, "AI read."));
    }

    @Test
    @DisplayName("a draft that fills anything makes a reviewed assessment a draft again")
    void aChangingDraftReopensAReview() {
        CandidateScorecard scorecard = scorecard();
        scorecard.record(List.of(capitalMarkets(7, null)), ScorecardReport.empty(), true, CONSULTANT, Instant.now());

        scorecard.applyAiDraft(List.of(treasury(6, "Group treasury.")), ScorecardReport.empty(), Instant.now());

        assertThat(scorecard.getStatus()).isEqualTo(ScorecardStatus.DRAFT);
        assertThat(scorecard.getReviewedAt()).isNull();
        assertThat(scorecard.getAiDraftedAt()).isNotNull();
    }

    @Test
    @DisplayName("editing an AI-drafted report section makes it the consultant's")
    void editingASectionTakesItFromTheAi() {
        CandidateScorecard scorecard = scorecard();
        scorecard.applyAiDraft(List.of(), report("AI summary.", "AI concerns."), Instant.now());

        scorecard.record(List.of(), report("AI summary.", "My concerns."), false, CONSULTANT, Instant.now());

        assertThat(scorecard.getAiDrafted())
                .containsExactly(CandidateScorecard.draftKeyOf(ScorecardReportSection.CAREER_SUMMARY));
    }

    @Test
    @DisplayName("nothing is left to draft once every score and section is a consultant's")
    void nothingOpenOnceEverythingIsTheConsultants() {
        CandidateScorecard scorecard = scorecard();
        List<String> brief = List.of(capitalMarkets(0, null).matchKey(), treasury(0, null).matchKey());
        assertThat(scorecard.hasOpenAmong(brief)).isTrue();

        scorecard.record(List.of(capitalMarkets(7, null), treasury(6, null)),
                new ScorecardReport("a", "b", "c", "d", "e"), false, CONSULTANT, Instant.now());
        assertThat(scorecard.hasOpenAmong(brief)).isFalse();

        scorecard.applyAiDraft(List.of(), new ScorecardReport(null, null, null, null, null), Instant.now());
        assertThat(scorecard.hasOpenAmong(List.of(capitalMarkets(0, null).matchKey(), "BEHAVIOURAL:new one")))
                .isTrue();
    }

    private static CandidateScorecard scorecard() {
        Person person = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL, details());
        Candidate candidate = Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), null, person,
                CandidateSource.MANUAL, PipelineTestStatuses.identified(), details());
        return CandidateScorecard.startFor(candidate, UUID.randomUUID());
    }

    private static CandidateDetails details() {
        return new CandidateDetails("Sample Person", null, null, null, null, null, null, null, null, null, null,
                null, null, null, CandidateCompensation.unknown(), CandidateProfile.empty(), null);
    }

    private static CriterionScore capitalMarkets(Integer score, String evidence) {
        return new CriterionScore(CompetencyPanel.TECHNICAL, "Capital markets", 60, score, evidence);
    }

    private static CriterionScore treasury(Integer score, String evidence) {
        return new CriterionScore(CompetencyPanel.TECHNICAL, "Treasury", 40, score, evidence);
    }

    private static ScorecardReport report(String careerSummary, String concerns) {
        return new ScorecardReport(careerSummary, null, concerns, null, null);
    }
}
