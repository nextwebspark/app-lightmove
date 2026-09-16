package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter four: the nationality and gender mix of the mapped pool, by seniority.
 *
 * <p><b>Gender is counted, never inferred.</b> {@code genderByLevel} totals only rows where a
 * researcher recorded one, and {@code genderUnrecorded} is everyone else — a chapter that guessed a
 * gender from a name would be stating a guess as a finding, and a mandate nobody has recorded reads
 * as unmeasured rather than as a pool of one gender.
 *
 * <p>{@code nationalities} is the leading groups plus "Other"; {@code unknownNationality} is
 * everyone with none on file.
 */
public record DiversityDto(List<String> levels, List<NationalityRowDto> nationalities,
                           int unknownNationality, long gccNationals,
                           List<GenderLevelRowDto> genderByLevel, int genderUnrecorded) {}
