package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.location.service.Countries;
import java.util.List;

/**
 * What research on a live profile came back with — the provider's answer already translated into this
 * feature's own vocabulary, plus the {@code vendor} that gave it. Nothing branches on that vendor; it
 * is carried so the row can record who answered, which is what makes the dataset's share of the work
 * measurable. It also means the fallback needs no plumbing of its own — whichever provider answered,
 * the answer says so itself.
 *
 * <p>{@code title} is the person's <i>current position</i>, never the profile headline — a headline
 * is a self-marketing sentence ("11+ years of… | MSc | SAFe"), not a job title. The employer triplet
 * ({@code employerName}, {@code employerLinkedinUrl}, {@code employerLogoUrl}) is what the enrichment
 * writer files into the mandate's universe when the candidate arrived unmapped.
 *
 * <p>{@code nationality}, {@code gender} and {@code yearsExperience} are null from every vendor
 * adapter — none of them answer background, only career and identity — and are filled in afterwards by
 * {@code CandidateBackgroundProposer} reading this same research (issue #458). They ride on this
 * record rather than a separate one because {@code Candidate.enrich} already merges one document onto
 * the candidate; a second parameter would only invite the two to disagree about which fields are set.
 */
public record EnrichedProfile(String title, String about, String employerName,
                              String employerLinkedinUrl, String employerLogoUrl,
                              String locationCity, String locationCountry,
                              List<CandidateCareerEntry> career,
                              List<CandidateEducationEntry> education,
                              List<String> skills, List<String> languages,
                              EnrichedPhoto photo, EnrichmentVendor vendor,
                              String nationality, Gender gender, Integer yearsExperience) {

    public EnrichedProfile {
        title = blankToNull(title);
        about = blankToNull(about);
        employerName = blankToNull(employerName);
        employerLinkedinUrl = blankToNull(employerLinkedinUrl);
        employerLogoUrl = blankToNull(employerLogoUrl);
        locationCity = Countries.cityOf(blankToNull(locationCity));
        locationCountry = Countries.nameOf(blankToNull(locationCountry));
        career = career == null ? List.of()
                : career.stream().filter(entry -> !entry.isEmpty()).toList();
        education = education == null ? List.of()
                : education.stream().filter(entry -> !entry.isEmpty()).toList();
        skills = skills == null ? List.of() : skills.stream().filter(skill -> blankToNull(skill) != null).toList();
        languages = languages == null ? List.of()
                : languages.stream().filter(language -> blankToNull(language) != null).toList();
        nationality = blankToNull(nationality);
    }

    /** A vendor adapter's own construction, its background fields not yet inferred. */
    public static EnrichedProfile researched(String title, String about, String employerName,
            String employerLinkedinUrl, String employerLogoUrl, String locationCity,
            String locationCountry, List<CandidateCareerEntry> career,
            List<CandidateEducationEntry> education, List<String> skills, List<String> languages,
            EnrichedPhoto photo, EnrichmentVendor vendor) {
        return new EnrichedProfile(title, about, employerName, employerLinkedinUrl, employerLogoUrl,
                locationCity, locationCountry, career, education, skills, languages, photo, vendor,
                null, null, null);
    }

    /** The same research, its photo filled in once the download completes — everything else unchanged. */
    public EnrichedProfile withPhoto(EnrichedPhoto photo) {
        return new EnrichedProfile(title, about, employerName, employerLinkedinUrl, employerLogoUrl,
                locationCity, locationCountry, career, education, skills, languages, photo, vendor,
                nationality, gender, yearsExperience);
    }

    /** The same research, with what {@code CandidateBackgroundProposer} could read from it. */
    public EnrichedProfile withBackground(String nationality, Gender gender, Integer yearsExperience) {
        return new EnrichedProfile(title, about, employerName, employerLinkedinUrl, employerLogoUrl,
                locationCity, locationCountry, career, education, skills, languages, photo, vendor,
                nationality, gender, yearsExperience);
    }
}
