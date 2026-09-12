package app.lightmove.api.position.dto;

import java.util.List;

/**
 * A reading of the attached document's step-one fields, to review and accept one row at a time.
 * Writes nothing: acceptance goes through the ordinary {@code PUT .../position/details} the screen
 * already has, one field at a time.
 *
 * @param extractionSource an {@code ExtractionSource} wire token — what produced this reading, and
 *                          how far it is worth trusting, exactly as {@code ImportPreviewResponse}
 *                          carries {@code mappingSource}
 */
public record PositionExtractionResponse(String extractionSource, List<ProposedFieldDto> fields) {}
