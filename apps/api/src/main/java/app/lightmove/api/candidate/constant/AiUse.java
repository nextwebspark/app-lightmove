package app.lightmove.api.candidate.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

/**
 * Each use of a language model that leaves something on a person's record, with the fixed words a subject access
 * export gives about it (#919, GDPR Art. 15(1)(h): "meaningful information about the logic involved"). Written from
 * the prompt each one runs, named by {@link #promptFile}; {@code AiLogicCatalogTest} makes every prompt either one of
 * these or a use that leaves nothing about anyone, so a new prompt cannot reach a person unexplained. The workspace's
 * #922 switches are named where they apply.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AiUse {

    NATIONALITY("candidate-nationality-system.st", "Nationality reading",
            "A language model reads your LinkedIn profile (career, education and languages, oldest first) with the"
                    + " notes and documents the team holds on you, contact details taken out, and places your"
                    + " nationality in one of eleven groups: the six Gulf states by name, Western expat, South Asian,"
                    + " Asian, Arab expat (non-GCC) and Other expat; or it answers Unknown. Where you live or work, a"
                    + " Gulf employer, a regional business school, a photo, appearance, religion or any inferred"
                    + " ethnicity are never counted as evidence. A Gulf nationality needs positive evidence, a name"
                    + " alone gives low confidence at most, and conflicting signals give Unknown. The model answers"
                    + " with a confidence and the evidence for and against, kept whole on your record. Only a"
                    + " high-confidence reading fills the field, marked as the AI's until a consultant changes it; a"
                    + " medium or low one is only a suggestion a consultant may accept. It runs only while the"
                    + " workspace holding your record has nationality inference switched on; switching it off clears"
                    + " every reading and every value the AI filled that no consultant had confirmed."),

    BACKGROUND("candidate-ai-enrich-system.st", "Background proposals: gender, years of experience, seniority",
            "When one of these is missing from your record, a language model reads your LinkedIn profile with the"
                    + " team's notes and documents on you, contact details taken out, and proposes its most probable"
                    + " value: gender from your first name and any pronouns in the profile; years of experience from"
                    + " your earliest dated role or your graduation year; and the level of your current role (Board,"
                    + " C-Suite, N-1, N-2 or N-3) from your title, the size of your employer and your career. A value"
                    + " already on your record is never replaced, and each proposal is marked as the AI's until a"
                    + " consultant confirms or changes it. Gender is proposed only while the workspace holding your"
                    + " record has gender inference switched on; switching it off clears every gender the AI filled"
                    + " that no consultant had confirmed."),

    ASSESSMENT_DRAFT("candidate-scorecard-draft-system.st", "Assessment draft",
            "When a consultant asks for it, a language model drafts the consultant's assessment of you against one"
                    + " position's brief: for each competency the brief scores, the evidence it found and a score from"
                    + " 0 to 10 against a fixed scale, and a written report. It reads the brief and the client's position"
                    + " description, the transcripts of calls the team recorded with you, the team's notes, your CV and"
                    + " profile and the other documents on file, contact details taken out; it is never given your"
                    + " compensation. Only your own words on a call count as evidence from it, never an interviewer's."
                    + " A score resting on your CV, your profile or your career alone is held between 3 and 7. The weighting and the total are calculated by the"
                    + " software, not by the model. Every drafted value stays marked as the AI's until a consultant"
                    + " edits it or reviews and submits the assessment. It can be asked for only while the workspace"
                    + " holding your record has AI-drafted assessments switched on."),

    MEETING_SUMMARY("meeting-summary-system.st", "Call notes from the Notetaker",
            "When a consultant records a call with you through the Notetaker, a language model reads the transcript"
                    + " and the position's brief, contact details taken out, and writes the call's note: an overview,"
                    + " the key points you made, and what you said about your motivation, package, notice and"
                    + " mobility. It states only what the transcript says. The note is filed under the consultant's"
                    + " name, who can edit it."),

    CALL_CHANGES("call-changes-system.st", "Changes suggested from a call",
            "After a recorded call, a language model compares the transcript with your record and lists what you"
                    + " said that would change it (such as your notice period, package, location, willingness to"
                    + " relocate or motivation) and the consultant's next steps, each with your words quoted and the"
                    + " time they were said. Nothing is written to your record until a consultant reviews each item,"
                    + " edits it if needed and applies it."),

    INTERVIEW_PLAN("interview-plan-system.st", "Interview questions",
            "Before a call, a language model may draft questions for the consultant: open questions for each"
                    + " competency of the position, and polite questions about facts missing from your record. It"
                    + " reads the brief, your profile and the team's notes and documents, and is told which facts are"
                    + " missing, never their values. The consultant edits and chooses every question; nothing it"
                    + " writes is said to you unchecked."),

    LIVE_COACH("interview-coach-system.st", "Live copilot during a call",
            "During a recorded call, where the consultant switched it on, a language model reads the transcript as"
                    + " it is spoken and shows only the consultant which competencies and missing facts your answers"
                    + " have covered and which prepared questions were asked. Only your own words count as evidence."
                    + " Its reading is kept with the recording; the lines streamed during the call are deleted when the"
                    + " call ends."),

    OUTREACH_OPENER("outreach-opener-system.st", "Opening line of an email",
            "For a first email to you in a sequence, a language model wrote one or two opening sentences from your"
                    + " LinkedIn profile and a few facts about the role. It is never told the hiring company and never"
                    + " states money. The consultant can review and edit it before the sequence starts."),

    EARLIER_ASSESSMENT(null, "An earlier AI assessment",
            "An earlier version of Uncava asked a language model for a summary of your fit for a position, with a"
                    + " score per assessment panel and the points for and against. It is no longer made or shown, and"
                    + " is included because a copy is still held on your record for that position.");

    /** The one statement every export carries, whichever uses applied (GDPR Art. 22). */
    public static final String NO_SOLELY_AUTOMATED_DECISION = "No decision about you is made by automated means"
            + " alone. Everything a language model proposes is shown to the team as the AI's, and a consultant decides"
            + " what to keep, what to change and whether to approach you. The models are given your record as data to"
            + " read; your contact details are taken out of what they read, and the compensation on your record and"
            + " the team's own columns are never sent.";

    /** The prompt under {@code src/main/resources/prompts}, or null for a use no prompt runs any more. */
    private final @Nullable String promptFile;
    private final String title;
    private final String logic;
}
