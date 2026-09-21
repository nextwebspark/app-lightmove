package app.lightmove.api.position.dto;

import app.lightmove.api.positiontemplate.dto.PositionTemplateSummary;
import java.util.List;

/**
 * A reading of the attached document's fields for one wizard step, to review and accept one row at a
 * time. Writes nothing: acceptance goes through the ordinary {@code PUT .../position/{step}} the
 * screen already has, one field at a time.
 *
 * @param extractionSource   an {@code ExtractionSource} wire token — what produced this reading, and
 *                           how far it is worth trusting, exactly as {@code ImportPreviewResponse}
 *                           carries {@code mappingSource}
 * @param suggestedTemplate  the brief template the extracted role title matches, offered as a
 *                           separate whole-brief opt-in — null on every response but step one's, and
 *                           null there too when nothing but the generic fallback would match
 * @param usualDirectReports the matched template's own direct reports, for the reporting step's
 *                           Suggested seats row — null on every response but step three's, and null
 *                           there too when the mandate's title matches no template
 */
public record PositionExtractionResponse(String extractionSource, List<ProposedFieldDto> fields,
                                         PositionTemplateSummary suggestedTemplate,
                                         List<String> usualDirectReports) {}
