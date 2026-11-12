package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;
import static app.lightmove.api.core.text.service.SuppliedText.browsableUrlOrNull;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.location.service.Countries;
import java.util.List;

/**
 * What a caller supplies about an executive, normalised once; only the name is required. {@code emails}/{@code phones}
 * add to the ledger (only the Contact section removes); {@code employerName} is overwritten by the triaged company's name.
 */
public record CandidateDetails(String fullName, String title, Seniority seniority,
                               String employerName, List<ContactEntry> emails,
                               List<ContactEntry> phones,
                               String linkedinUrl, String locationCountry, String locationCity,
                               String nationality, Gender gender, Integer yearsExperience,
                               String summary, String note,
                               CandidateCompensation compensation, CandidateProfile profile,
                               String sourceUrl) {

    public CandidateDetails {
        fullName = fullName == null ? null : fullName.trim();
        title = blankToNull(title);
        employerName = blankToNull(employerName);
        emails = supplied(emails);
        phones = supplied(phones);
        linkedinUrl = browsableUrlOrNull(linkedinUrl);
        locationCountry = Countries.nameOf(blankToNull(locationCountry));
        locationCity = Countries.cityOf(blankToNull(locationCity));
        // Not a country: a nationality is a demonym ("Egyptian"), which the catalog cannot resolve
        // and the field's own hint insists is a different fact from where somebody lives.
        nationality = blankToNull(nationality);
        summary = blankToNull(summary);
        note = blankToNull(note);
        compensation = compensation == null ? CandidateCompensation.unknown() : compensation;
        profile = profile == null ? CandidateProfile.empty() : profile;
        sourceUrl = browsableUrlOrNull(sourceUrl);
    }

    /** The same details, with the employer the mandate's own company row already knows it by. */
    public CandidateDetails employedAt(String resolvedEmployerName) {
        return new CandidateDetails(fullName, title, seniority, resolvedEmployerName, emails,
                phones, linkedinUrl, locationCountry, locationCity, nationality, gender,
                yearsExperience, summary, note, compensation, profile, sourceUrl);
    }

    private static List<ContactEntry> supplied(List<ContactEntry> entries) {
        return entries == null ? List.of()
                : entries.stream().filter(entry -> entry != null && !entry.isBlank()).toList();
    }
}
