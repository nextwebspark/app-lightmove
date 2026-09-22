package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter four: the nationality and gender mix of the mapped pool, by seniority.
 *
 * <p><b>This chapter states whatever the row holds, and nothing more.</b> {@code genderByLevel} totals
 * every row with a gender on file — a researcher's own entry, or a captured profile's AI-suggested
 * value the row still carries (issue #458) — and {@code genderUnrecorded} is everyone else. It does
 * not distinguish the two: a value nobody has reviewed is still what the row states today, and a
 * mandate with nothing on file at all reads as unmeasured rather than as a pool of one gender.
 *
 * <p>{@code genderWithoutLevel} is the recorded genders of executives with no seniority on file.
 * A captured executive arrives without a level, so without it a gender recorded on one was counted
 * nowhere: not in a level's split, and not as unrecorded either.
 *
 * <p>{@code nationalities} is the leading groups plus "Other"; {@code unknownNationality} is
 * everyone with none on file.
 */
public record DiversityDto(List<String> levels, List<NationalityRowDto> nationalities,
                           int unknownNationality, long gccNationals,
                           List<GenderLevelRowDto> genderByLevel, GenderSplitDto genderWithoutLevel,
                           int genderUnrecorded) {}
