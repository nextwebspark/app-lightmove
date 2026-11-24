package app.lightmove.api.position.dto;

import app.lightmove.api.positiontemplate.dto.PositionTemplateSummary;
import java.util.List;

/**
 * A reading of the attached document's fields for one step; writes nothing (acceptance is the step's PUT).
 *
 * @param extractionSource   an {@code ExtractionSource} wire token
 * @param suggestedTemplate  step one only; null when only the generic fallback would match
 * @param usualDirectReports step three only; null when the title matches no template
 * @param promptVersion      the prompt version that answered; null when the heuristic did, or the bundled prompt.
 *                           The step's PUT sends it back as {@code readingVersion} with what the reading filled.
 */
public record PositionExtractionResponse(String extractionSource, List<ProposedFieldDto> fields,
                                         PositionTemplateSummary suggestedTemplate,
                                         List<String> usualDirectReports, Integer promptVersion) {}
