package app.lightmove.api.promptregistry.constant;

import app.lightmove.api.core.llm.constant.PromptKind;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The prompts the platform console lists, by registry id: the name and the line a super admin reads, and how the
 * answer is read. {@code PlatformPromptTest} holds this to the shipped {@code prompts/*.st}, both ways.
 * {@code decisionsCounted}: a screen asks a person to keep or change this prompt's answer, and reports it through
 * {@code PromptOutcomes}, so its usage has a kept share.
 */
@Getter
@RequiredArgsConstructor
public enum PlatformPrompt {

    ASSESSMENT_DRAFT("candidate-scorecard-draft", "Assessment draft",
            "Drafts a score and its evidence for each competency of the brief.", PromptKind.STRUCTURED),
    ASSISTANT("assistant-core", "Assistant",
            "The assistant's standing instructions on every screen.", PromptKind.TOOL_CALLING),
    MEETING_SUMMARY("meeting-summary", "Meeting summary",
            "Summarises a recorded call into decisions and next steps.", PromptKind.STRUCTURED),
    OUTREACH_OPENER("outreach-opener", "Outreach opener",
            "Writes the first line of an email from a person's profile.", PromptKind.STRUCTURED, true),
    CALL_CHANGES("call-changes", "Call changes",
            "Lists what a call changed about a candidate.", PromptKind.STRUCTURED),
    INTERVIEW_PLAN("interview-plan", "Interview plan",
            "Plans the questions for a call from the brief and the notes.", PromptKind.STRUCTURED),
    INTERVIEW_COACH("interview-coach", "Interview coach",
            "Suggests the next question during a call.", PromptKind.STRUCTURED),
    PROFILE_ENRICHMENT("candidate-ai-enrich", "Profile enrichment",
            "Reads seniority and function from a profile.", PromptKind.STRUCTURED, true),
    NATIONALITY("candidate-nationality", "Nationality",
            "Proposes a nationality from a profile, with its reason.", PromptKind.STRUCTURED, true),
    SOURCING_SEARCH_WORDS("executive-sourcing-words", "Sourcing search words",
            "Turns a brief into the words a people search uses.", PromptKind.STRUCTURED),
    SHORTLIST("recruiter-shortlist", "Shortlist",
            "Ranks the people found for a company against the brief.", PromptKind.PROSE),
    IMPORT_COLUMN_MAPPING("import-column-mapping", "Import column mapping",
            "Matches a spreadsheet's columns to Uncava's fields.", PromptKind.STRUCTURED),
    POSITION_DETAILS("position-extract-details", "Position details",
            "Reads the role's details from a position description.", PromptKind.STRUCTURED),
    POSITION_CONTEXT("position-extract-context", "Position context",
            "Reads the company context from a position description.", PromptKind.STRUCTURED),
    POSITION_REPORTING("position-extract-reporting", "Position reporting",
            "Reads the reporting line from a position description.", PromptKind.STRUCTURED),
    POSITION_ASSESSMENT("position-extract-assessment", "Position assessment",
            "Reads the competencies and weights from a position description.", PromptKind.STRUCTURED),
    EVAL_JUDGE("eval-judge", "Eval judge",
            "Compares two answers to the same eval case against a rubric and picks the better.",
            PromptKind.STRUCTURED);

    private final String promptId;
    private final String displayName;
    private final String description;
    private final PromptKind kind;
    private final boolean decisionsCounted;

    PlatformPrompt(String promptId, String displayName, String description, PromptKind kind) {
        this(promptId, displayName, description, kind, false);
    }

    public static Optional<PlatformPrompt> of(String promptId) {
        return Arrays.stream(values()).filter(prompt -> prompt.promptId.equals(promptId)).findFirst();
    }
}
