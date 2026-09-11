package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;
import static app.lightmove.api.core.text.service.SuppliedText.browsableUrlOrNull;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.common.location.service.Countries;

/**
 * Everything a caller supplies about an executive, normalised once on the way in. Only the name is
 * required: research arrives in pieces, and refusing the row until the compensation is established
 * would send that name into a spreadsheet instead.
 *
 * <p>{@code employerName} is the company as the row will remember it — overwritten with the triaged
 * company's name where the candidate is mapped to one, so the two cannot drift.
 * {@code linkedinUrl} goes through {@link app.lightmove.api.core.text.service.SuppliedText}, being
 * the field most likely to be pasted from somewhere else.
 */
public record CandidateDetails(String fullName, String title, Seniority seniority,
                               CandidateStatus status, String employerName, String email, String phone,
                               String linkedinUrl, String locationCountry, String locationCity,
                               String nationality, Integer yearsExperience, String summary, String note,
                               CandidateCompensation compensation, CandidateProfile profile,
                               String sourceUrl) {

    public CandidateDetails {
        fullName = fullName == null ? null : fullName.trim();
        title = blankToNull(title);
        employerName = blankToNull(employerName);
        email = blankToNull(email);
        phone = blankToNull(phone);
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
        return new CandidateDetails(fullName, title, seniority, status, resolvedEmployerName, email,
                phone, linkedinUrl, locationCountry, locationCity, nationality, yearsExperience,
                summary, note, compensation, profile, sourceUrl);
    }
}
