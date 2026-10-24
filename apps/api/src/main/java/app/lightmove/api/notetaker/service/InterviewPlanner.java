package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.enrichment.candidate.model.ExecutiveReadingText;
import app.lightmove.api.notetaker.constant.InterviewQuestionSection;
import app.lightmove.api.notetaker.constant.InterviewQuestionSource;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetency;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * One model call drafting the questions for a call with an executive: fit questions per competency of the brief, a
 * polite question per fact the record is missing, and at most two clarifiers from the career. The model is told only
 * which facts are missing, never what a held one says, and never a package figure; code keeps only questions it can
 * place — a competency id the brief has, a gap that was listed — and holds each section to its size.
 */
@Component
class InterviewPlanner {

    private static final String PROMPT_ID = "interview-plan";
    private static final String BLOCKED = "{\"guard\":\"" + BlockedAnswer.MARKER + "\"}";
    static final int LONGEST_QUESTION = 400;
    static final int LONGEST_LINE = 200;
    private static final int MOST_PER_COMPETENCY = 2;
    private static final int MOST_CLARIFIERS = 2;

    static final Map<RecordGap, String> GAP_WORDS = Map.of(
            RecordGap.NOTICE, "their notice period",
            RecordGap.PACKAGE, "their current package (salary, bonus, allowances)",
            RecordGap.LOCATION, "where they live now",
            RecordGap.RELOCATION, "whether they would relocate for the right role",
            RecordGap.MOTIVATION, "what would make them move",
            RecordGap.EXPECTED_PACKAGE, "what package they would expect");

    private final StructuredPrompt prompt;

    InterviewPlanner(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /**
     * Throws {@code VALIDATION_FAILED} when the guard stopped what is on file and {@code AI_UNAVAILABLE} when nothing
     * usable came back; a failure of the model call itself propagates for the caller to answer as unavailable.
     */
    List<InterviewQuestion> draft(AssessmentBrief brief, ExecutiveReadingText executive, Set<String> evidenced,
                                  Set<RecordGap> gaps) {
        Map<String, BriefCompetency> competencyById = MeetingSummariser.idsOf(brief);
        PlanAnswer answer = prompt.ask(PlanAnswer.class, user -> user.text("""
                THE POSITION
                {position}

                COMPETENCIES THE TEAM HAS ALREADY EVIDENCED
                {evidenced}

                FACTS THE RECORD IS MISSING (gap name: what it is)
                {gaps}

                THE EXECUTIVE (their LinkedIn profile)
                {profile}

                NOTES ON FILE (the team's own, newest first)
                {notes}

                DOCUMENTS ON FILE
                {documents}

                EARLIER CALLS (the record of each call your team recorded with them for this position, newest first)
                {calls}
                """)
                .param("position", MeetingSummariser.positionOf(brief, competencyById))
                .param("evidenced", evidencedText(competencyById, evidenced))
                .param("gaps", gaps.isEmpty() ? "none" : gaps.stream()
                        .map(gap -> "- %s: %s".formatted(gap.name(), GAP_WORDS.get(gap)))
                        .collect(Collectors.joining("\n")))
                .param("profile", executive.profile())
                .param("notes", executive.notes())
                .param("documents", executive.documents())
                .param("calls", executive.calls()));
        if (answer == null) {
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
        if (BlockedAnswer.matches(answer.guard())) {
            if (executive.callCount() > 0) {
                // Anyone on a call speaks into its record and nobody can reword it, so a remark there costs the
                // earlier calls, never the whole plan.
                return draft(brief, executive.withoutCalls(), evidenced, gaps);
            }
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Something on file for this person reads like an instruction to the AI, so it was not read. "
                            + "Write the questions by hand.");
        }
        List<InterviewQuestion> questions = questionsOf(answer, competencyById, gaps);
        if (questions.isEmpty()) {
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
        return questions;
    }

    static List<InterviewQuestion> questionsOf(PlanAnswer answer, Map<String, BriefCompetency> competencyById,
                                               Set<RecordGap> gaps) {
        List<InterviewQuestion> kept = new ArrayList<>();
        Map<String, Integer> perCompetency = new HashMap<>();
        for (PlanAnswer.Fit fit : answer.fit() == null ? List.<PlanAnswer.Fit>of() : answer.fit()) {
            BriefCompetency competency = fit.id() == null ? null
                    : competencyById.get(fit.id().strip().toLowerCase(Locale.ROOT));
            String text = clip(fit.question(), LONGEST_QUESTION);
            if (competency == null || text == null
                    || perCompetency.merge(competency.name(), 1, Integer::sum) > MOST_PER_COMPETENCY) {
                continue;
            }
            kept.add(new InterviewQuestion(newId(), InterviewQuestionSection.FIT, competency.panel(),
                    competency.name(), null, text, clip(fit.listenFor(), LONGEST_LINE), null, true,
                    InterviewQuestionSource.AI));
        }
        Set<RecordGap> asked = EnumSet.noneOf(RecordGap.class);
        for (PlanAnswer.Gap gap : answer.gaps() == null ? List.<PlanAnswer.Gap>of() : answer.gaps()) {
            RecordGap named = gapOf(gap.gap());
            String text = clip(gap.question(), LONGEST_QUESTION);
            if (named == null || text == null || !gaps.contains(named) || !asked.add(named)) {
                continue;
            }
            kept.add(new InterviewQuestion(newId(), InterviewQuestionSection.GAP, null, null, named, text, null, null,
                    true, InterviewQuestionSource.AI));
        }
        int clarifiers = 0;
        for (PlanAnswer.Clarify clarify : answer.clarify() == null ? List.<PlanAnswer.Clarify>of()
                : answer.clarify()) {
            String text = clip(clarify.question(), LONGEST_QUESTION);
            if (text == null || clarifiers >= MOST_CLARIFIERS) {
                continue;
            }
            clarifiers++;
            kept.add(new InterviewQuestion(newId(), InterviewQuestionSection.CLARIFY, null, null, null, text, null,
                    clip(clarify.why(), LONGEST_LINE), true, InterviewQuestionSource.AI));
        }
        return List.copyOf(kept);
    }

    private static String evidencedText(Map<String, BriefCompetency> competencyById, Set<String> evidenced) {
        String listed = competencyById.entrySet().stream()
                .filter(entry -> evidenced.contains(keyOf(entry.getValue().name())))
                .map(entry -> "- " + entry.getKey())
                .collect(Collectors.joining("\n"));
        return listed.isEmpty() ? "none" : listed;
    }

    static String keyOf(String competencyName) {
        return competencyName == null ? "" : competencyName.strip().toLowerCase(Locale.ROOT);
    }

    static RecordGap gapOf(String named) {
        if (named == null) {
            return null;
        }
        try {
            return RecordGap.valueOf(named.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    static String clip(String value, int longest) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String flat = value.strip().replaceAll("\\s+", " ");
        return flat.length() <= longest ? flat : flat.substring(0, longest).strip();
    }

    record PlanAnswer(String guard, List<Fit> fit, List<Gap> gaps, List<Clarify> clarify) {

        record Fit(String id, String question, String listenFor) {}

        record Gap(String gap, String question) {}

        record Clarify(String question, String why) {}
    }
}
