package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.outreach.model.OpenerBrief;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One model call per person: the {@code {{opener}}} of their first email. Its input is the
 * {@link CandidateDossier} allowlist and an {@link OpenerBrief}, so contacts, package, notes and the
 * hiring company can never reach the prompt. Every failure answers empty; the consultant writes one.
 */
@Service
@Slf4j
public class OutreachOpenerDrafter {

    private static final String PROMPT_ID = "outreach-opener";
    private static final String BLOCKED = "{\"opener\":\"" + BlockedAnswer.MARKER + "\"}";
    private static final String NOT_STATED = "not stated";
    private static final int MAX_OPENER_LENGTH = 600;

    private final StructuredPrompt prompt;

    public OutreachOpenerDrafter(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    public Optional<String> draft(CandidateDossier dossier, OpenerBrief brief) {
        try {
            ModelAnswer answered = ask(dossier, brief);
            if (answered == null || answered.opener() == null || answered.opener().isBlank()
                    || BlockedAnswer.matches(answered.opener())) {
                return Optional.empty();
            }
            String opener = answered.opener().trim();
            return Optional.of(opener.length() > MAX_OPENER_LENGTH ? opener.substring(0, MAX_OPENER_LENGTH) : opener);
        } catch (RuntimeException e) {
            log.warn("Outreach opener not drafted: {}", e.toString());
            return Optional.empty();
        }
    }

    private ModelAnswer ask(CandidateDossier dossier, OpenerBrief brief) {
        return prompt.ask(ModelAnswer.class, user -> user.text("""
                THE EXECUTIVE (their LinkedIn profile)
                Name: {name}
                Current title: {title}
                Current employer: {company}
                Location: {location}
                About: {about}

                Career history, most recent first:
                {career}

                Education:
                {education}

                Skills: {skills}

                THE ROLE
                Title: {roleTitle}
                Level: {seniority}
                Sector: {sector}
                Location: {roleLocation}
                """)
                .param("name", orNotStated(dossier.fullName()))
                .param("title", orNotStated(dossier.title()))
                .param("company", orNotStated(dossier.companyName()))
                .param("location", placeOf(dossier.locationCity(), dossier.locationCountry()))
                .param("about", orNotStated(dossier.summary()))
                .param("career", careerOf(dossier.career()))
                .param("education", educationOf(dossier.education()))
                .param("skills", dossier.skills().isEmpty() ? NOT_STATED : String.join(", ", dossier.skills()))
                .param("roleTitle", orNotStated(brief.roleTitle()))
                .param("seniority", orNotStated(brief.seniority()))
                .param("sector", orNotStated(brief.sector()))
                .param("roleLocation", placeOf(brief.locationCity(), brief.locationCountry())));
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
                .map(school -> "- %s, %s".formatted(
                        school.school() == null ? "unknown school" : school.school(),
                        school.degree() == null ? "degree unknown" : school.degree()))
                .collect(Collectors.joining("\n"));
    }

    private static String placeOf(String city, String country) {
        String place = Stream.of(city, country).filter(Objects::nonNull).collect(Collectors.joining(", "));
        return place.isEmpty() ? NOT_STATED : place;
    }

    private static String orNotStated(String value) {
        return value == null || value.isBlank() ? NOT_STATED : value;
    }

    private record ModelAnswer(String opener) {}
}
