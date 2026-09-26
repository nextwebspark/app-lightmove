package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.model.AssessmentSourceLink;
import app.lightmove.api.candidate.model.CandidateAiAssessment;
import app.lightmove.api.candidate.model.CandidateAiEnrichment;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.CompetencyPanelAssessment;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.common.constant.NationalityGroup;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.position.dto.AssessmentDto;
import app.lightmove.api.position.dto.CompetencyDto;
import app.lightmove.api.position.dto.PositionResponse;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One Google-grounded model call over a candidate's {@link CandidateDossier} and the mandate's brief:
 * the background still missing, and a 1–10 technical and behavioural reading with the pages behind it.
 * Every failure answers empty — nothing is ever stored in place of a real answer.
 */
@Service
@Slf4j
public class CandidateAiEnricher {

    private static final String PROMPT_ID = "candidate-ai-enrich";

    private static final int MIN_PLAUSIBLE_YEARS = 0;
    private static final int MAX_PLAUSIBLE_YEARS = 60;
    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 10;
    private static final int MAX_POINTS_PER_LIST = 5;
    private static final int MAX_SOURCES = 8;
    private static final String NOT_STATED = "not stated";

    private static final String BLOCKED = "{\"summary\":\"" + BlockedAnswer.MARKER + "\"}";

    private final StructuredPrompt prompt;

    public CandidateAiEnricher(StructuredPromptFactory prompts) {
        this.prompt = prompts.createSearchGrounded(PROMPT_ID, BLOCKED);
    }

    public Optional<CandidateAiEnrichment> enrich(CandidateDossier dossier, PositionResponse brief) {
        try {
            ModelAnswer answered = ask(dossier, brief);
            if (answered == null || BlockedAnswer.matches(answered.summary())) {
                return Optional.empty();
            }
            return Optional.of(new CandidateAiEnrichment(backgroundOf(answered, dossier.missingBackground()),
                    new CandidateAiAssessment(trimmed(answered.summary()), panelOf(answered.technical()),
                            panelOf(answered.behavioural()), sourcesOf(answered.sources()),
                            Instant.now().toString())));
        } catch (RuntimeException e) {
            // No credentials, Vertex unreachable or an unbindable answer: store nothing this time.
            log.warn("Candidate AI enrichment skipped: {}", e.toString());
            return Optional.empty();
        }
    }

    private ModelAnswer ask(CandidateDossier dossier, PositionResponse brief) {
        AssessmentDto assessment = brief.assessment();
        return prompt.ask(ModelAnswer.class, user -> user.text("""
                THE CANDIDATE (their LinkedIn profile, already researched — do not search it again)
                Name: {name}
                Current title: {title}
                Current employer: {company}
                Location: {location}
                LinkedIn profile: {linkedin}
                About: {about}

                Career history, most recent first:
                {career}

                Education:
                {education}

                Skills: {skills}
                Languages: {languages}

                Background fields still to propose: {missing}

                THE ROLE
                Title: {roleTitle}
                Seniority: {seniority}
                Technical share of the assessment: {technicalShare}%

                Technical competencies:
                {technical}

                Behavioural competencies:
                {behavioural}

                Selection criteria:
                {criteria}
                """)
                .param("name", orNotStated(dossier.fullName()))
                .param("title", orNotStated(dossier.title()))
                .param("company", orNotStated(dossier.companyName()))
                .param("location", locationOf(dossier))
                .param("linkedin", orNotStated(dossier.linkedinUrl()))
                .param("about", orNotStated(dossier.summary()))
                .param("career", careerOf(dossier.career()))
                .param("education", educationOf(dossier.education()))
                .param("skills", listOf(dossier.skills()))
                .param("languages", listOf(dossier.languages()))
                .param("missing", missingOf(dossier.missingBackground()))
                .param("roleTitle", orNotStated(brief.details() == null ? null : brief.details().roleTitle()))
                .param("seniority", brief.details() == null || brief.details().seniority() == null
                        ? NOT_STATED : brief.details().seniority().name())
                .param("technicalShare", assessment == null ? 50 : assessment.technicalShare())
                .param("technical", competenciesOf(assessment == null ? List.of() : assessment.technical()))
                .param("behavioural", competenciesOf(assessment == null ? List.of() : assessment.behavioural()))
                .param("criteria", assessment == null || assessment.criteria().isEmpty() ? NOT_STATED
                        : assessment.criteria().stream()
                                .map(criterion -> "- (%s) %s".formatted(
                                        criterion.mode().name().toLowerCase(Locale.ROOT), criterion.text()))
                                .collect(Collectors.joining("\n"))));
    }

    /** Only what was missing when the run began; {@code Candidate.proposeBackground} re-checks at write. */
    private static InferredBackground backgroundOf(ModelAnswer answered, Set<BackgroundField> missing) {
        return new InferredBackground(
                missing.contains(BackgroundField.NATIONALITY) ? nationalityOf(answered) : null,
                missing.contains(BackgroundField.GENDER) ? genderOf(answered) : null,
                missing.contains(BackgroundField.YEARS_EXPERIENCE) ? yearsExperienceOf(answered) : null);
    }

    /** One of the nine canonical groups, or null — never the model's own spelling. */
    private static String nationalityOf(ModelAnswer answered) {
        NationalityGroup group = NationalityGroup.ofLabel(answered.nationality());
        return group == null ? null : group.value();
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

    private static CompetencyPanelAssessment panelOf(ModelPanel panel) {
        if (panel == null) {
            return new CompetencyPanelAssessment(null, List.of(), List.of());
        }
        Integer score = panel.score() == null || panel.score() < MIN_SCORE || panel.score() > MAX_SCORE
                ? null : panel.score();
        return new CompetencyPanelAssessment(score, pointsOf(panel.positives()), pointsOf(panel.negatives()));
    }

    private static List<String> pointsOf(List<String> points) {
        return points == null ? List.of() : points.stream()
                .map(CandidateAiEnricher::trimmed)
                .filter(Objects::nonNull)
                .limit(MAX_POINTS_PER_LIST)
                .toList();
    }

    /** Absolute http(s) links, one per URL; LinkedIn dropped, since the profile is already the dossier. */
    private static List<AssessmentSourceLink> sourcesOf(List<ModelSource> sources) {
        if (sources == null) {
            return List.of();
        }
        Map<String, AssessmentSourceLink> kept = new LinkedHashMap<>();
        for (ModelSource source : sources) {
            String url = source == null ? null : trimmed(source.url());
            if (url != null && isCitableWebPage(url) && kept.size() < MAX_SOURCES) {
                kept.putIfAbsent(url, new AssessmentSourceLink(url, trimmed(source.title())));
            }
        }
        return List.copyOf(kept.values());
    }

    private static boolean isCitableWebPage(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http")) && !host.isEmpty()
                    && !host.equals("linkedin.com") && !host.endsWith(".linkedin.com");
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String orNotStated(String value) {
        return value == null ? NOT_STATED : value;
    }

    private static String locationOf(CandidateDossier dossier) {
        String location = Stream.of(dossier.locationCity(), dossier.locationCountry())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return location.isEmpty() ? NOT_STATED : location;
    }

    private static String listOf(List<String> values) {
        return values.isEmpty() ? NOT_STATED : String.join(", ", values);
    }

    private static String missingOf(Set<BackgroundField> missing) {
        return missing.isEmpty() ? "none" : missing.stream().map(BackgroundField::key).collect(Collectors.joining(", "));
    }

    private static String careerOf(List<CandidateCareerEntry> career) {
        return career.isEmpty() ? NOT_STATED : career.stream()
                .map(post -> "- %s at %s (%s)".formatted(
                        post.title() == null ? "unknown role" : post.title(),
                        post.company() == null ? "unknown company" : post.company(),
                        post.period() == null ? "period unknown" : post.period()))
                .collect(Collectors.joining("\n"));
    }

    private static String educationOf(List<CandidateEducationEntry> education) {
        return education.isEmpty() ? NOT_STATED : education.stream()
                .map(school -> "- %s, %s (%s)".formatted(
                        school.school() == null ? "unknown school" : school.school(),
                        school.degree() == null ? "degree unknown" : school.degree(),
                        school.period() == null ? "period unknown" : school.period()))
                .collect(Collectors.joining("\n"));
    }

    private static String competenciesOf(List<CompetencyDto> competencies) {
        return competencies.isEmpty() ? "none in the brief" : competencies.stream()
                .map(competency -> "- %s (weight %d)%s".formatted(competency.name(), competency.weight(),
                        competency.description() == null ? "" : ": " + competency.description()))
                .collect(Collectors.joining("\n"));
    }

    private record ModelAnswer(String nationality, String gender, Integer yearsExperience, String summary,
                               ModelPanel technical, ModelPanel behavioural, List<ModelSource> sources) {}

    private record ModelPanel(Integer score, List<String> positives, List<String> negatives) {}

    private record ModelSource(String url, String title) {}
}
