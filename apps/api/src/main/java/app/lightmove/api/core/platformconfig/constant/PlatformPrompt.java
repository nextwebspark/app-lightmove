package app.lightmove.api.core.platformconfig.constant;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every system prompt a super admin may override on Settings → Platform → Prompts, one per shipped file under
 * {@code prompts/}. The file stays the default; the answer schemas beside them are never editable, because code binds
 * the answer to them. {@code PlatformPromptCatalogTest} keeps this list and the directory in step.
 */
public enum PlatformPrompt {

    ASSISTANT_CORE("assistant-core", "prompts/assistant-core.st", "Assistant"),
    RECRUITER_SHORTLIST("recruiter-shortlist", "Candidate shortlist"),
    CALL_CHANGES("call-changes", "Suggest changes from a call"),
    CANDIDATE_AI_ENRICH("candidate-ai-enrich", "Suggest background"),
    CANDIDATE_NATIONALITY("candidate-nationality", "Nationality reading"),
    CANDIDATE_SCORECARD_DRAFT("candidate-scorecard-draft", "Assess with AI"),
    EXECUTIVE_SOURCING_SPEC("executive-sourcing-spec", "Find executives: title words"),
    EXECUTIVE_SOURCING_REFINE("executive-sourcing-refine", "Find executives: another round"),
    IMPORT_COLUMN_MAPPING("import-column-mapping", "Spreadsheet columns"),
    INTERVIEW_COACH("interview-coach", "Live copilot"),
    INTERVIEW_PLAN("interview-plan", "Prepare questions"),
    MEETING_SUMMARY("meeting-summary", "Call notes"),
    OUTREACH_OPENER("outreach-opener", "Outreach opener"),
    POSITION_EXTRACT_DETAILS("position-extract-details", "Position description: role"),
    POSITION_EXTRACT_CONTEXT("position-extract-context", "Position description: context"),
    POSITION_EXTRACT_REPORTING("position-extract-reporting", "Position description: reporting"),
    POSITION_EXTRACT_ASSESSMENT("position-extract-assessment", "Position description: assessment");

    private final String id;
    private final String resource;
    private final String label;

    PlatformPrompt(String id, String label) {
        this(id, "prompts/" + id + "-system.st", label);
    }

    PlatformPrompt(String id, String resource, String label) {
        this.id = id;
        this.resource = resource;
        this.label = label;
    }

    public static Optional<PlatformPrompt> byId(String id) {
        return Arrays.stream(values()).filter(prompt -> prompt.id.equals(id)).findFirst();
    }

    public String id() {
        return id;
    }

    /** The shipped file on the classpath. */
    public String resource() {
        return resource;
    }

    public String label() {
        return label;
    }
}
