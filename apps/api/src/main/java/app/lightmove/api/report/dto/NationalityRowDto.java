package app.lightmove.api.report.dto;

import java.util.List;

/**
 * One nationality's headcount by level. {@code gcc} marks a Gulf national, which localisation rules
 * turn on; {@code unclassified} counts executives with no seniority on file.
 */
public record NationalityRowDto(String nationality, boolean gcc, List<LevelCountDto> byLevel,
                                int unclassified, int total) {}
