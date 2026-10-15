package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One model call over a candidate's {@link CandidateDossier}, and the notes and documents the team filed on them: the
 * most probable gender, years of experience and seniority still missing. Nationality is {@link CandidateNationalityClassifier}'s, never this call's. Every failure
 * answers empty — nothing is ever stored in place of a real answer.
 */
@Service
@Slf4j
public class CandidateAiEnricher {

    private static final String PROMPT_ID = "candidate-ai-enrich";

    private static final int MIN_PLAUSIBLE_YEARS = 0;
    private static final int MAX_PLAUSIBLE_YEARS = 60;

    private static final String BLOCKED = "{\"gender\":\"" + BlockedAnswer.MARKER + "\"}";

    /** The fields this call proposes; with none of them missing there is nothing to ask. */
    public static final Set<BackgroundField> INFERRED = Set.of(
            BackgroundField.GENDER, BackgroundField.YEARS_EXPERIENCE, BackgroundField.SENIORITY);

    private final StructuredPrompt prompt;

    public CandidateAiEnricher(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    public Optional<InferredBackground> infer(CandidateDossier dossier) {
        return infer(dossier, MaterialText.NONE);
    }

    Optional<InferredBackground> infer(CandidateDossier dossier, MaterialText material) {
        try {
            ModelAnswer answered = ask(dossier, material);
            if (answered == null || BlockedAnswer.matches(answered.gender())) {
                return Optional.empty();
            }
            return Optional.of(backgroundOf(answered, dossier.missingBackground()));
        } catch (RuntimeException e) {
            // No credentials, Vertex unreachable or an unbindable answer: store nothing this time.
            log.warn("Candidate AI background skipped: {}", e.toString());
            return Optional.empty();
        }
    }

    private ModelAnswer ask(CandidateDossier dossier, MaterialText material) {
        return prompt.ask(ModelAnswer.class, user -> user.text("""
                THE CANDIDATE (their LinkedIn profile)
                {profile}

                NOTES ON FILE (the team's own, newest first)
                {notes}

                DOCUMENTS ON FILE
                {documents}

                Background fields still to propose: {missing}
                Today's date: {today}
                """)
                .param("profile", DossierText.of(dossier))
                .param("notes", material.notes())
                .param("documents", material.documents())
                .param("missing", missingOf(dossier.missingBackground()))
                .param("today", LocalDate.now(ZoneOffset.UTC).toString()));
    }

    /** Only what was missing when the run began; {@code Person.proposeBackground} re-checks at write. */
    private static InferredBackground backgroundOf(ModelAnswer answered, Set<BackgroundField> missing) {
        return new InferredBackground(
                missing.contains(BackgroundField.GENDER) ? genderOf(answered) : null,
                missing.contains(BackgroundField.YEARS_EXPERIENCE) ? yearsExperienceOf(answered) : null,
                missing.contains(BackgroundField.SENIORITY) ? seniorityOf(answered) : null);
    }

    /** One of the five levels, spelled as the candidate API spells it ("N-1"), or null. */
    private static Seniority seniorityOf(ModelAnswer answered) {
        return answered.seniority() == null ? null : Seniority.fromValue(answered.seniority().trim());
    }

    private static Gender genderOf(ModelAnswer answered) {
        return answered.gender() == null ? null
                : Gender.fromValue(answered.gender().trim().toLowerCase(Locale.ROOT));
    }

    /** An implausible figure is discarded rather than stored as a hallucination. */
    private static Integer yearsExperienceOf(ModelAnswer answered) {
        Integer years = answered.yearsExperience();
        return years == null || years < MIN_PLAUSIBLE_YEARS || years > MAX_PLAUSIBLE_YEARS ? null : years;
    }

    /** Nationality is never listed here: the classifier answers it, and this call must not. */
    private static String missingOf(Set<BackgroundField> missing) {
        String listed = missing.stream()
                .filter(INFERRED::contains)
                .map(BackgroundField::key)
                .collect(Collectors.joining(", "));
        return listed.isEmpty() ? "none" : listed;
    }

    private record ModelAnswer(String gender, Integer yearsExperience, String seniority) {}
}
