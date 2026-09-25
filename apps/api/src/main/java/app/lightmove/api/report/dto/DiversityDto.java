package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter four: the nationality and gender mix of the mapped pool, by seniority.
 *
 * <p><b>Gender is counted as the row holds it.</b> {@code genderByLevel} totals every row with one on
 * file — a researcher's entry or a flagged AI proposal — and {@code genderUnrecorded} is everyone else,
 * so a mandate with nothing on file reads as unmeasured rather than as a pool of one gender.
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
