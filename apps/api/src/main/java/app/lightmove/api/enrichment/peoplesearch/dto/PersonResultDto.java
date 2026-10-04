package app.lightmove.api.enrichment.peoplesearch.dto;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.CandidateEducationEntryDto;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails;
import java.util.List;
import java.util.UUID;

/**
 * One person on a page of a people search, read the way a filed candidate's profile is — the same
 * {@code toEnrichedProfile} reading, so the preview is what Add to universe would file. {@code held} is
 * whether this mandate already maps them — {@code candidateId} says as whom: they were still returned,
 * and billed, because ContactOut cannot exclude a person. The location is the record's {@code city} — the
 * whole LinkedIn place line. {@code details} is null where the provider's own record was not kept.
 */
public record PersonResultDto(String linkedinSlug, String fullName, String title, String companyName,
                              String companyLinkedinUrl, String companyLogoUrl, String location,
                              String countryCode, String photoUrl, String profileUrl, String about,
                              List<CandidateCareerEntryDto> career, List<CandidateEducationEntryDto> education,
                              List<String> skills, List<String> languages, PersonDetailsDto details,
                              UUID candidateId, boolean held) {

    public static PersonResultDto of(BrightDataPerson person, ContactOutProfileDetails details, UUID candidateId) {
        EnrichedProfile profile = BrightDataPersonProfiles.toEnrichedProfile(person, EnrichmentVendor.CONTACTOUT);
        return new PersonResultDto(person.linkedinId(), person.name(), profile.title(),
                profile.employerName() != null ? profile.employerName() : person.currentCompanyName(),
                profile.employerLinkedinUrl(), profile.employerLogoUrl(),
                person.city() != null ? person.city() : person.location(), person.countryCode(),
                person.usableAvatarUrl(), person.profileUrl(), profile.about(),
                profile.career().stream()
                        .map(post -> new CandidateCareerEntryDto(post.company(), post.title(), post.period(),
                                post.location()))
                        .toList(),
                profile.education().stream()
                        .map(school -> new CandidateEducationEntryDto(school.school(), school.degree(), school.period()))
                        .toList(),
                profile.skills(), profile.languages(), details == null ? null : PersonDetailsDto.of(details),
                candidateId, candidateId != null);
    }
}
