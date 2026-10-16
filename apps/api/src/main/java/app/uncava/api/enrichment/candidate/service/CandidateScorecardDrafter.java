package app.uncava.api.enrichment.candidate.service;

import app.uncava.api.candidate.model.AiScorecardDraft;
import app.uncava.api.candidate.model.CandidateDossier;
import app.uncava.api.candidate.model.CandidateScorecard;
import app.uncava.api.candidate.model.CriterionScore;
import app.uncava.api.candidate.model.ScorecardReport;
import app.uncava.api.common.constant.CompetencyPanel;
import app.uncava.api.core.llm.model.BlockedAnswer;
import app.uncava.api.core.llm.service.StructuredPrompt;
import app.uncava.api.core.llm.service.StructuredPromptFactory;
import app.uncava.api.position.model.AssessmentBrief;
import app.uncava.api.position.model.BriefCompetency;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One model call drafting a consultant's assessment from the profile, the team's notes and the documents on file: a
 * score out of 10 and its evidence per competency, and the five report sections. The total out of 100 is never the
 * model's — {@code ScorecardScoring} weighs the scores exactly as it weighs a consultant's.
 */
@Service
@Slf4j
public class CandidateScorecardDrafter {

    private static final String PROMPT_ID = "candidate-scorecard-draft";

    private static final String BLOCKED =
            "{\"scores\":[],\"report\":{\"careerSummary\":\"" + BlockedAnswer.MARKER + "\"}}";

    private final StructuredPrompt prompt;

    public CandidateScorecardDrafter(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /**
     * Empty when there is nothing to score, the model could not be reached, or it answered nothing usable — a draft
     * with no score and no section is never written or charged. A blocked prompt is refused instead.
     */
    Optional<AiScorecardDraft> draft(CandidateDossier dossier, AssessmentBrief brief, MaterialText material) {
        Map<String, BriefCompetency> competencyById = idsOf(brief.competencies().competencies());
        if (competencyById.isEmpty()) {
            return Optional.empty();
        }
        ModelAnswer answered;
        try {
            answered = ask(dossier, brief, material, competencyById);
        } catch (RuntimeException e) {
            log.warn("Candidate scorecard draft skipped: {}", e.toString());
            return Optional.empty();
        }
        if (answered == null) {
            return Optional.empty();
        }
        if (answered.report() != null && BlockedAnswer.matches(answered.report().careerSummary())) {
            throw AiRefusals.blockedText();
        }
        AiScorecardDraft draft = new AiScorecardDraft(scoresOf(answered.scores(), competencyById),
                reportOf(answered.report()));
        return draft.scores().isEmpty() && draft.report().equals(ScorecardReport.empty())
                ? Optional.empty() : Optional.of(draft);
    }

    private ModelAnswer ask(CandidateDossier dossier, AssessmentBrief brief, MaterialText material,
                            Map<String, BriefCompetency> competencyById) {
        return prompt.ask(ModelAnswer.class, user -> user.text("""
                THE CANDIDATE (their LinkedIn profile)
                {profile}

                NOTES ON FILE (the team's own, newest first)
                {notes}

                DOCUMENTS ON FILE
                {documents}

                THE ROLE
                Title: {roleTitle}
                Level: {seniority}
                Department: {department}
                Location: {location}
                About the role: {narrative}
                Business driver: {businessDriver}
                Strategic priorities: {priorities}

                Responsibilities:
                {responsibilities}

                Selection criteria:
                {criteria}

                Competencies to score (technical carries {technicalShare}% of the assessment, behavioural the rest):
                {competencies}
                """)
                .param("profile", DossierText.of(dossier))
                .param("notes", material.notes())
                .param("documents", material.documents())
                .param("roleTitle", DossierText.orNotStated(brief.roleTitle()))
                .param("seniority", brief.seniority() == null ? DossierText.NOT_STATED : brief.seniority().value())
                .param("department", DossierText.orNotStated(brief.department()))
                .param("location", DossierText.placeOf(brief.locationCity(), brief.locationCountry()))
                .param("narrative", DossierText.orNotStated(brief.narrative()))
                .param("businessDriver", DossierText.orNotStated(brief.businessDriver()))
                .param("priorities", DossierText.listOf(brief.strategicPriorities()))
                .param("responsibilities", linesOf(brief.responsibilities()))
                .param("criteria", brief.criteria().isEmpty() ? DossierText.NOT_STATED : brief.criteria().stream()
                        .map(criterion -> "- (%s) %s".formatted(
                                criterion.mode().name().toLowerCase(Locale.ROOT), criterion.text()))
                        .collect(Collectors.joining("\n")))
                .param("technicalShare", brief.competencies().technicalShare())
                .param("competencies", competencyById.entrySet().stream()
                        .map(entry -> "- %s [%s, weight %d] %s%s".formatted(entry.getKey(),
                                entry.getValue().panel().value(), entry.getValue().weight(), entry.getValue().name(),
                                entry.getValue().description() == null ? "" : ": " + entry.getValue().description()))
                        .collect(Collectors.joining("\n"))));
    }

    /** T1, T2, … for the technical panel and B1, B2, … for the behavioural, each in the brief's own order. */
    static Map<String, BriefCompetency> idsOf(List<BriefCompetency> competencies) {
        Map<String, BriefCompetency> byId = new LinkedHashMap<>();
        int technical = 0;
        int behavioural = 0;
        for (BriefCompetency competency : competencies) {
            String id = competency.panel() == CompetencyPanel.TECHNICAL ? "T" + ++technical : "B" + ++behavioural;
            byId.put(id, competency);
        }
        return byId;
    }

    private static List<CriterionScore> scoresOf(List<ModelScore> answered,
                                                 Map<String, BriefCompetency> competencyById) {
        Map<String, CriterionScore> byKey = new LinkedHashMap<>();
        for (ModelScore score : answered == null ? List.<ModelScore>of() : answered) {
            BriefCompetency competency = score.id() == null ? null
                    : competencyById.get(score.id().trim().toUpperCase(Locale.ROOT));
            if (competency == null) {
                continue;
            }
            CriterionScore drafted = new CriterionScore(competency.panel(), competency.name(), competency.weight(),
                    scoreOf(score.score()), trimmed(score.evidence(), CandidateScorecard.MAX_EVIDENCE));
            if (!drafted.holdsNothing()) {
                byKey.putIfAbsent(drafted.matchKey(), drafted);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private static Integer scoreOf(Integer score) {
        return score == null || score < CandidateScorecard.MIN_SCORE || score > CandidateScorecard.MAX_SCORE
                ? null : score;
    }

    private static ScorecardReport reportOf(ModelReport answered) {
        if (answered == null) {
            return ScorecardReport.empty();
        }
        int max = CandidateScorecard.MAX_REPORT_SECTION;
        return new ScorecardReport(trimmed(answered.careerSummary(), max), trimmed(answered.coreValue(), max),
                trimmed(answered.concerns(), max), trimmed(answered.workingStyle(), max),
                trimmed(answered.motivation(), max));
    }

    private static String trimmed(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).strip();
    }

    private static String linesOf(List<String> lines) {
        return lines.isEmpty() ? DossierText.NOT_STATED
                : lines.stream().map(line -> "- " + line).collect(Collectors.joining("\n"));
    }

    private record ModelAnswer(List<ModelScore> scores, ModelReport report) {}

    private record ModelScore(String id, String evidence, Integer score) {}

    private record ModelReport(String careerSummary, String coreValue, String concerns, String workingStyle,
                               String motivation) {}
}
