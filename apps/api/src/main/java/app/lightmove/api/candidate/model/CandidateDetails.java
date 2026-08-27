package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;
import static app.lightmove.api.core.text.service.SuppliedText.browsableUrlOrNull;

import app.lightmove.api.candidate.constant.CandidateSeniority;
import app.lightmove.api.candidate.constant.CandidateStatus;

/**
 * Everything a caller supplies about an executive, normalised once on the way in.
 *
 * <p>Only the name is required, for the same reason a captured company needs only its name: research
 * arrives in pieces. A researcher who has met someone at a conference has a name, a company and a
 * rough title, and refusing the row until the compensation is established would send that name into a
 * spreadsheet — which is the behaviour these screens exist to replace.
 *
 * <p>{@code employerName} is the company as the row will remember it. Where the candidate is mapped to
 * one of the mandate's triaged companies the service overwrites it with that company's name, so the
 * two cannot drift; where they are not, it is whatever the researcher typed.
 *
 * <p>{@code linkedinUrl} goes through the same gate as a captured company's addresses — see
 * {@link app.lightmove.api.core.text.service.SuppliedText}. It matters more here than there: a profile
 * URL is the field most likely to be pasted from somewhere else, and the plugin will eventually post
 * one it read off a page.
 */
public record CandidateDetails(String fullName, String title, CandidateSeniority seniority,
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
        locationCountry = blankToNull(locationCountry);
        locationCity = blankToNull(locationCity);
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
