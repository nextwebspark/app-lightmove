package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.position.dto.AssessmentDto;
import app.lightmove.api.position.dto.CompetencyDto;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.dto.ResponsibilityDto;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * As much of the brief as the two model calls may see: what the role is, where it sits and what it
 * is measured on. Compensation and the mandate's internal context are deliberately absent — neither
 * decides who fits a title, and both are the sensitive half of a brief.
 */
public record SourcingBrief(String roleTitle, Seniority seniority, String department, String locationCity,
                            String locationCountry, List<String> responsibilities, String narrative,
                            List<String> technicalCompetencies) {

    private static final int MAX_RESPONSIBILITIES = 10;
    private static final int MAX_NARRATIVE = 600;

    public static SourcingBrief of(PositionResponse brief) {
        PositionDetailsDto details = brief.details();
        AssessmentDto assessment = brief.assessment();
        return new SourcingBrief(
                details.roleTitle(),
                details.seniority(),
                details.department(),
                details.locationCity(),
                details.locationCountry(),
                details.responsibilities() == null ? List.of() : details.responsibilities().stream()
                        .map(ResponsibilityDto::text)
                        .limit(MAX_RESPONSIBILITIES)
                        .toList(),
                truncate(details.narrative()),
                assessment == null || assessment.technical() == null ? List.of()
                        : assessment.technical().stream().map(CompetencyDto::name).toList());
    }

    /** The brief's country as ISO-2, or null when it names none the catalog knows. */
    public String countryCode() {
        return Countries.codeOf(locationCountry);
    }

    /** "Dubai, United Arab Emirates", either half alone, or null when the brief names neither. */
    public String locationLine() {
        String line = Stream.of(locationCity, locationCountry)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
        return line.isEmpty() ? null : line;
    }

    private static String truncate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flattened = text.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_NARRATIVE ? flattened : flattened.substring(0, MAX_NARRATIVE) + "…";
    }
}
