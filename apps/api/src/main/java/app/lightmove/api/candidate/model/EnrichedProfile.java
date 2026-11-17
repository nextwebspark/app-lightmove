package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.location.service.Countries;
import java.util.List;

/**
 * A live profile's research, in this feature's vocabulary, with the {@code vendor} that answered (recorded, never
 * branched on). {@code title} is the current position, never the headline; the employer triplet is what gets filed
 * into the universe for an unmapped candidate. {@code fullName} is read only by the Add form's profile lookup, which
 * has no page to read a name off: research never renames anyone. {@code publicProfileUrl} is the profile's vanity
 * address as the vendor knows it, which heals a capture made at LinkedIn's member-id address.
 */
public record EnrichedProfile(String title, String about, String employerName,
                              String employerLinkedinUrl, String employerLogoUrl,
                              String locationCity, String locationCountry,
                              List<CandidateCareerEntry> career,
                              List<CandidateEducationEntry> education,
                              List<String> skills, List<String> languages,
                              EnrichedPhoto photo, EnrichmentVendor vendor, String fullName,
                              String publicProfileUrl) {

    public EnrichedProfile(String title, String about, String employerName,
                           String employerLinkedinUrl, String employerLogoUrl,
                           String locationCity, String locationCountry,
                           List<CandidateCareerEntry> career,
                           List<CandidateEducationEntry> education,
                           List<String> skills, List<String> languages,
                           EnrichedPhoto photo, EnrichmentVendor vendor, String fullName) {
        this(title, about, employerName, employerLinkedinUrl, employerLogoUrl, locationCity, locationCountry,
                career, education, skills, languages, photo, vendor, fullName, null);
    }

    public EnrichedProfile(String title, String about, String employerName,
                           String employerLinkedinUrl, String employerLogoUrl,
                           String locationCity, String locationCountry,
                           List<CandidateCareerEntry> career,
                           List<CandidateEducationEntry> education,
                           List<String> skills, List<String> languages,
                           EnrichedPhoto photo, EnrichmentVendor vendor) {
        this(title, about, employerName, employerLinkedinUrl, employerLogoUrl, locationCity, locationCountry,
                career, education, skills, languages, photo, vendor, null, null);
    }

    public EnrichedProfile {
        fullName = blankToNull(fullName);
        publicProfileUrl = blankToNull(publicProfileUrl);
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
                locationCity, locationCountry, career, education, skills, languages, fetched, vendor, fullName,
                publicProfileUrl);
    }

    public EnrichedProfile withPublicProfileUrl(String vanityUrl) {
        return new EnrichedProfile(title, about, employerName, employerLinkedinUrl, employerLogoUrl,
                locationCity, locationCountry, career, education, skills, languages, photo, vendor, fullName,
                vanityUrl);
    }
}
