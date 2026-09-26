package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter four. Gender is counted as the row holds it, so a pool with nothing on file reads as
 * unmeasured; {@code genderWithoutLevel} keeps captured executives, who arrive without a level, counted.
 */
public record DiversityDto(List<String> levels, List<NationalityRowDto> nationalities,
                           int unknownNationality, long gccNationals,
                           List<GenderLevelRowDto> genderByLevel, GenderSplitDto genderWithoutLevel,
                           int genderUnrecorded) {}
