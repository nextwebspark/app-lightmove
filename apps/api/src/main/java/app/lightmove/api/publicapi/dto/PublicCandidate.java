package app.lightmove.api.publicapi.dto;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.dto.CandidateResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An executive as a key reads them. A background value a model proposed and no researcher has since
 * confirmed (seniority, nationality, gender, years of experience) is sent as null: outside the product
 * there is no badge to say it is a guess.
 */
@Schema(name = "Candidate", description = "An executive a position has mapped. Seniority, nationality, gender and "
        + "years of experience are null until a person has recorded or confirmed them")
public record PublicCandidate(
        @Schema(description = "This executive's id within the position") UUID id,
        @Schema(description = "The person's id, the same on every position that maps them, so one person can be followed across positions")
        UUID personId,
        @Schema(description = "The position's company they are mapped at; null where their employer is not in its universe",
                nullable = true)
        UUID companyId,
        @Schema(description = "Their employer's name", nullable = true, example = "ACWA Power") String companyName,
        @Schema(description = "Their full name", example = "Layla Haddad") String fullName,
        @Schema(description = "Their current title", nullable = true, example = "Chief Financial Officer") String title,
        @Schema(description = "Their level against the role", nullable = true,
                allowableValues = {"Board", "C-Suite", "N-1", "N-2", "N-3"}, example = "C-Suite")
        String seniority,
        @Schema(description = "Where they stand on this position",
                allowableValues = {"identified", "contacted", "engaged", "interested", "notInterested", "offLimits",
                        "outOfScope"},
                example = "contacted")
        String status,
        @Schema(description = "Their LinkedIn profile", nullable = true,
                example = "https://www.linkedin.com/in/layla-haddad") String linkedinUrl,
        @Schema(description = "The city they live in", nullable = true, example = "Dubai") String city,
        @Schema(description = "The country they live in", nullable = true, example = "United Arab Emirates")
        String country,
        @Schema(description = "Their nationality group", nullable = true, example = "Emirati") String nationality,
        @Schema(description = "Their gender, where recorded", nullable = true,
                allowableValues = {"female", "male", "other"})
        String gender,
        @Schema(description = "Years of experience", nullable = true, example = "18") Integer yearsExperience,
        @Schema(description = "A short profile summary", nullable = true) String summary,
        @Schema(description = "Their career, latest first") List<PublicCareerEntry> career,
        @Schema(description = "Their education") List<PublicEducationEntry> education,
        @Schema(description = "Languages they speak", example = "[\"Arabic\", \"English\"]") List<String> languages,
        @Schema(description = "Skills from their profile") List<String> skills,
        @Schema(description = "When the position mapped them") Instant addedAt,
        @Schema(description = "Every email and phone known for them. Null unless the key holds candidates.contacts:read",
                nullable = true)
        PublicContacts contacts,
        @Schema(description = "Their current package. Null unless the key holds candidates.compensation:read",
                nullable = true)
        PublicCompensation compensation
) {

    public static PublicCandidate of(CandidateResponse candidate, boolean withContacts, boolean withCompensation) {
        Set<String> proposed = candidate.aiInferredFields() == null ? Set.of() : candidate.aiInferredFields();
        return new PublicCandidate(candidate.id(), candidate.personId(), candidate.triageCompanyId(),
                candidate.companyName(), candidate.fullName(), candidate.title(),
                recorded(proposed, BackgroundField.SENIORITY, candidate.seniority()),
                candidate.status(), candidate.linkedinUrl(), candidate.locationCity(), candidate.locationCountry(),
                recorded(proposed, BackgroundField.NATIONALITY, candidate.nationality()),
                recorded(proposed, BackgroundField.GENDER, candidate.gender()),
                recorded(proposed, BackgroundField.YEARS_EXPERIENCE, candidate.yearsExperience()),
                candidate.summary(),
                candidate.career().stream()
                        .map(entry -> new PublicCareerEntry(entry.company(), entry.title(), entry.period(),
                                entry.location()))
                        .toList(),
                candidate.education().stream()
                        .map(school -> new PublicEducationEntry(school.school(), school.degree(), school.period()))
                        .toList(),
                candidate.languages(), candidate.skills(), candidate.addedAt(),
                withContacts ? PublicContacts.of(candidate.contacts()) : null,
                withCompensation ? PublicCompensation.of(candidate.compensation()) : null);
    }

    private static <T> T recorded(Set<String> proposed, BackgroundField field, T value) {
        return proposed.contains(field.key()) ? null : value;
    }
}
