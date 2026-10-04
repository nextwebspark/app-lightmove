package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.NationalityConfidence;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.common.constant.NationalityGroup;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One model call that reads a candidate's nationality against an evidence rubric — residence is not
 * nationality, a GCC group needs positive evidence, and "Unknown" is an answer. Its own prompt rather
 * than a field of {@link CandidateAiEnricher}'s, because that call must always propose and this one
 * must be allowed not to. Every failure answers empty.
 */
@Service
@Slf4j
public class CandidateNationalityClassifier {

    private static final String PROMPT_ID = "candidate-nationality";
    private static final String BLOCKED = "{\"category\":\"" + BlockedAnswer.MARKER + "\"}";

    private static final int MAX_EVIDENCE_PER_SIDE = 5;
    private static final String NOT_STATED = "not stated";
    private static final String NO_RULE = "none";
    private static final Set<String> RULES = Set.of("A", "B", "C", "D", "E", NO_RULE);

    private final StructuredPrompt prompt;

    public CandidateNationalityClassifier(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    public Optional<NationalityReading> classify(CandidateDossier dossier) {
        try {
            ModelAnswer answered = ask(dossier);
            if (answered == null || BlockedAnswer.matches(answered.category())) {
                return Optional.empty();
            }
            return Optional.of(readingOf(answered));
        } catch (RuntimeException e) {
            log.warn("Candidate nationality classification skipped: {}", e.toString());
            return Optional.empty();
        }
    }

    private ModelAnswer ask(CandidateDossier dossier) {
        return prompt.ask(ModelAnswer.class, user -> user.text("""
                Name: {name}
                Headline: {headline}
                About: {about}
                Languages: {languages}
                Current location (residence — not evidence of nationality): {location}

                Education:
                {education}

                Experience, OLDEST FIRST:
                {experience}
                """)
                .param("name", orNotStated(dossier.fullName()))
                .param("headline", headlineOf(dossier))
                .param("about", orNotStated(dossier.summary()))
                .param("languages", dossier.languages().isEmpty() ? NOT_STATED : String.join(", ", dossier.languages()))
                .param("location", locationOf(dossier))
                .param("education", educationOf(dossier.education()))
                .param("experience", experienceOf(dossier.career())));
    }

    /** The model's spelling never reaches the row: a label no group carries is Unknown. */
    private static NationalityReading readingOf(ModelAnswer answered) {
        NationalityGroup group = NationalityGroup.ofLabel(answered.category());
        NationalityConfidence confidence = answered.confidence() == null ? null
                : NationalityConfidence.fromValue(answered.confidence().trim().toLowerCase(Locale.ROOT));
        String rule = answered.rule() == null ? NO_RULE : answered.rule().trim();
        return new NationalityReading(
                group == null ? NationalityReading.UNKNOWN : group.value(),
                (confidence == null ? NationalityConfidence.LOW : confidence).value(),
                evidenceOf(answered.evidenceFor()),
                evidenceOf(answered.evidenceAgainst()),
                RULES.contains(rule) ? rule : NO_RULE,
                Instant.now().toString());
    }

    private static List<String> evidenceOf(List<String> lines) {
        return lines == null ? List.of() : lines.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .limit(MAX_EVIDENCE_PER_SIDE)
                .toList();
    }

    private static String headlineOf(CandidateDossier dossier) {
        if (dossier.title() == null) {
            return orNotStated(dossier.companyName());
        }
        return dossier.companyName() == null ? dossier.title() : dossier.title() + " at " + dossier.companyName();
    }

    private static String locationOf(CandidateDossier dossier) {
        String location = Stream.of(dossier.locationCity(), dossier.locationCountry())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return location.isEmpty() ? NOT_STATED : location;
    }

    private static String educationOf(List<CandidateEducationEntry> education) {
        return education.isEmpty() ? NOT_STATED : education.stream()
                .map(school -> "- %s, %s (%s)".formatted(
                        school.school() == null ? "unknown institution" : school.school(),
                        school.degree() == null ? "degree unknown" : school.degree(),
                        school.period() == null ? "years unknown" : school.period()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Vendors and the drawer both store a career newest first, and the posts this rubric leans on —
     * the first job and where it was — would otherwise arrive last.
     */
    static String experienceOf(List<CandidateCareerEntry> career) {
        if (career.isEmpty()) {
            return NOT_STATED;
        }
        List<CandidateCareerEntry> oldestFirst = new ArrayList<>(career);
        Collections.reverse(oldestFirst);
        return oldestFirst.stream()
                .map(post -> "- %s, %s%s (%s)".formatted(
                        post.title() == null ? "unknown role" : post.title(),
                        post.company() == null ? "unknown company" : post.company(),
                        post.location() == null ? "" : ", " + post.location(),
                        post.period() == null ? "years unknown" : post.period()))
                .collect(Collectors.joining("\n"));
    }

    private static String orNotStated(String value) {
        return value == null ? NOT_STATED : value;
    }

    private record ModelAnswer(String category, String confidence,
                               @JsonProperty("evidence_for") List<String> evidenceFor,
                               @JsonProperty("evidence_against") List<String> evidenceAgainst,
                               @JsonProperty("rule_applied") String rule) {}
}
