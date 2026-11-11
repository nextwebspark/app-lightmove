package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.location.service.Countries;
import java.util.List;

/**
 * A live profile's research, in this feature's vocabulary, with the {@code vendor} that answered (recorded, never
 * branched on). {@code title} is the current position, never the headline; the employer triplet is what gets filed
 * into the universe for an unmapped candidate.
 */
public record EnrichedProfile(String title, String about, String employerName,
                              String employerLinkedinUrl, String employerLogoUrl,
                              String locationCity, String locationCountry,
                              List<CandidateCareerEntry> career,
                              List<CandidateEducationEntry> education,
                              List<String> skills, List<String> languages,
                              EnrichedPhoto photo, EnrichmentVendor vendor) {

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
    }

    public EnrichedProfile withPhoto(EnrichedPhoto fetched) {
        return new EnrichedProfile(title, about, employerName, employerLinkedinUrl, employerLogoUrl,
                locationCity, locationCountry, career, education, skills, languages, fetched, vendor);
    }
}
