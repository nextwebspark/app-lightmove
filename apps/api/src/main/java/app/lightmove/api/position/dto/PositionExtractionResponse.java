package app.lightmove.api.position.dto;

import app.lightmove.api.positiontemplate.dto.PositionTemplateSummary;
import java.util.List;

/**
 * A reading of the attached document's fields for one step; writes nothing (acceptance is the step's PUT).
 *
 * @param extractionSource   an {@code ExtractionSource} wire token
 * @param suggestedTemplate  step one only; null when only the generic fallback would match
 * @param usualDirectReports step three only; null when the title matches no template
 */
public record PositionExtractionResponse(String extractionSource, List<ProposedFieldDto> fields,
                                         PositionTemplateSummary suggestedTemplate,
                                         List<String> usualDirectReports) {}
