package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter four: the nationality mix of the mapped pool, by seniority.
 *
 * <p>Nationality only. Gender is not recorded on a candidate, and a chapter that inferred it from a
 * name would be stating a guess as a finding, so nothing here claims it. {@code nationalities} is
 * the leading groups plus "Other"; {@code unknownNationality} is everyone with none on file.
 */
public record DiversityDto(List<String> levels, List<NationalityRowDto> nationalities,
                           int unknownNationality, long gccNationals) {}
