package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetencies;
import app.lightmove.api.position.model.BriefCompetency;
import app.lightmove.api.position.model.BriefCriterion;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The assessment draft's Try it: an invented finance chief and an invented brief. Nobody here exists. */
@Component
@RequiredArgsConstructor
class CandidateScorecardTrial implements PromptTrial {

    private static final CandidateDossier EXECUTIVE = new CandidateDossier(
            "Layla Haddad", "Chief Financial Officer", "Northwind Logistics", "Dubai", "United Arab Emirates", null,
            "Finance chief of a listed regional logistics group. Led its 2023 refinancing and the integration of two "
                    + "acquired carriers.",
            List.of(new CandidateCareerEntry("Northwind Logistics", "Chief Financial Officer", "2021 - present",
                            "Dubai"),
                    new CandidateCareerEntry("Northwind Logistics", "Group Financial Controller", "2017 - 2021",
                            "Dubai"),
                    new CandidateCareerEntry("Harbour & Vale", "Audit Senior Manager", "2010 - 2017", "London")),
            List.of(new CandidateEducationEntry("University of Manchester", "BSc Accounting and Finance",
                    "2003 - 2006")),
            List.of("Treasury", "Debt refinancing", "IFRS reporting", "Post-merger integration"),
            List.of("English", "Arabic"),
            Set.of());

    private static final AssessmentBrief BRIEF = new AssessmentBrief(
            "Group Chief Financial Officer", Seniority.C_SUITE, "Finance", "Riyadh", "Saudi Arabia",
            "Runs finance for a family-owned industrial group preparing to list within three years.",
            List.of("Lead the group's listing readiness", "Own treasury and the lending relationships",
                    "Build a finance team that closes the books in five working days"),
            "The group plans a listing and its finance function has never reported to public-market standards.",
            List.of("Listing readiness", "Stronger controls"),
            List.of(new BriefCriterion("Has been the finance chief of a listed company", CriterionMode.REQUIRED),
                    new BriefCriterion("Has raised or refinanced debt above USD 200m", CriterionMode.REQUIRED)),
            new BriefCompetencies(List.of(
                    new BriefCompetency(CompetencyPanel.TECHNICAL, "Capital markets",
                            "Raising and refinancing debt and equity", 50),
                    new BriefCompetency(CompetencyPanel.TECHNICAL, "Financial reporting",
                            "Public-company reporting and controls", 50),
                    new BriefCompetency(CompetencyPanel.BEHAVIOURAL, "Leading change",
                            "Takes a team through a change it did not ask for", 60),
                    new BriefCompetency(CompetencyPanel.BEHAVIOURAL, "Board presence",
                            "Holds the confidence of a board and its owners", 40)), 60));

    private static final String NOTES = """
            - 2026-10-14, Researcher: Spoke for twenty minutes. She ran the 2023 refinancing herself, USD 310m \
            across four lenders, and said the hardest part was the covenant reset.
            - 2026-10-02, Researcher: A former colleague describes her as calm in front of the board, less patient \
            with slow teams.""";

    private static final String TRANSCRIPTS = """
            Call of 2026-10-20
            03:10 Layla Haddad: When we bought the two carriers I moved both finance teams onto one close calendar \
            in four months. We lost two people who would not change, and I would do it the same way again.
            07:45 Layla Haddad: I have presented to the audit committee every quarter since 2021.""";

    private static final String DOCUMENTS = """
            Board paper, March 2024 (written by the candidate): Proposes a single treasury policy for the group \
            and a hedging limit of 60% of forecast fuel spend.""";

    private final CandidateScorecardDrafter drafter;

    @Override
    public String promptId() {
        return "candidate-scorecard-draft";
    }

    @Override
    public String inputName() {
        return "Strong direct evidence";
    }

    @Override
    public String inputDescription() {
        return "A fictional finance chief with a profile, two notes, one call and a board paper. "
                + "No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return CandidateScorecardDrafter.USER_MESSAGE;
    }

    @Override
    public void run() {
        drafter.draft(EXECUTIVE, BRIEF, new MaterialText(NOTES, DOCUMENTS, TRANSCRIPTS, 2, 1, 1));
    }
}
