package app.lightmove.api.enrichment.peoplesearch.dto;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;

/**
 * One person on a page of a people search, as the grid draws them. {@code held} is whether this mandate
 * already maps them: they were still returned, and billed, because ContactOut cannot exclude a person.
 * The location is the record's {@code city} — the whole LinkedIn place line in both vendors' records.
 */
public record PersonResultDto(String linkedinSlug, String fullName, String title, String companyName,
                              String companyLinkedinUrl, String location, String countryCode, String photoUrl,
                              String profileUrl, boolean held) {

    public static PersonResultDto of(BrightDataPerson person, boolean held) {
        return new PersonResultDto(person.linkedinId(), person.name(), person.position(),
                person.currentCompanyName(),
                person.currentCompany() == null ? null : person.currentCompany().link(),
                person.city() != null ? person.city() : person.location(), person.countryCode(),
                person.usableAvatarUrl(), person.profileUrl(), held);
    }
}
