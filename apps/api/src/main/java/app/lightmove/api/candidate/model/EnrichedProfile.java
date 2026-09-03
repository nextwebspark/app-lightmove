package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import java.util.List;

/**
 * What research on a live profile came back with — the provider's answer already translated into this
 * feature's own vocabulary, so {@link Candidate#enrich} never learns which vendor answered.
 *
 * <p>{@code title} is the person's <i>current position</i>, never the profile headline — a headline
 * is a self-marketing sentence ("11+ years of… | MSc | SAFe"), not a job title. The employer triplet
 * ({@code employerName}, {@code employerLinkedinUrl}, {@code employerLogoUrl}) is what the enrichment
 * writer files into the mandate's universe when the candidate arrived unmapped.
 */
public record EnrichedProfile(String title, String about, String employerName,
                              String employerLinkedinUrl, String employerLogoUrl,
                              String locationCity, String locationCountry,
                              List<CandidateCareerEntry> career,
                              List<CandidateEducationEntry> education,
                              List<String> skills, List<String> languages,
                              EnrichedPhoto photo) {

    public EnrichedProfile {
        title = blankToNull(title);
        about = blankToNull(about);
        employerName = blankToNull(employerName);
        employerLinkedinUrl = blankToNull(employerLinkedinUrl);
        employerLogoUrl = blankToNull(employerLogoUrl);
        locationCity = blankToNull(locationCity);
        locationCountry = blankToNull(locationCountry);
        career = career == null ? List.of()
                : career.stream().filter(entry -> !entry.isEmpty()).toList();
        education = education == null ? List.of()
                : education.stream().filter(entry -> !entry.isEmpty()).toList();
        skills = skills == null ? List.of() : skills.stream().filter(skill -> blankToNull(skill) != null).toList();
        languages = languages == null ? List.of()
                : languages.stream().filter(language -> blankToNull(language) != null).toList();
    }
}
