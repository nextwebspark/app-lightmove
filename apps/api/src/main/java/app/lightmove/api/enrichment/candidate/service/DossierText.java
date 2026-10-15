package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A {@link CandidateDossier} written out as the profile block of a prompt, career most recent first. */
final class DossierText {

    static final String NOT_STATED = "not stated";

    private DossierText() {}

    static String of(CandidateDossier dossier) {
        return """
                Name: %s
                Current title: %s
                Current employer: %s
                Location: %s
                LinkedIn profile: %s
                About: %s

                Career history, most recent first:
                %s

                Education:
                %s

                Skills: %s
                Languages: %s""".formatted(
                orNotStated(dossier.fullName()),
                orNotStated(dossier.title()),
                orNotStated(dossier.companyName()),
                locationOf(dossier),
                orNotStated(dossier.linkedinUrl()),
                orNotStated(dossier.summary()),
                careerOf(dossier.career()),
                educationOf(dossier.education()),
                listOf(dossier.skills()),
                listOf(dossier.languages()));
    }

    static String orNotStated(String value) {
        return value == null || value.isBlank() ? NOT_STATED : value;
    }

    static String listOf(List<String> values) {
        return values.isEmpty() ? NOT_STATED : String.join(", ", values);
    }

    private static String locationOf(CandidateDossier dossier) {
        String location = Stream.of(dossier.locationCity(), dossier.locationCountry())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return location.isEmpty() ? NOT_STATED : location;
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
}
