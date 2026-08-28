package app.lightmove.api.position.dto;

/**
 * The whole position brief, grouped the way the screen walks it. Every write answers with this same
 * shape, so a step that saved never has to be merged into what the screen was already holding.
 *
 * <p>{@code document} is null until one is attached; {@code publication.publishedAt} is null until
 * somebody publishes.
 */
public record PositionResponse(
        PositionDetailsDto details,
        MandateContextDto context,
        ReportingStructureDto reporting,
        CompensationDto compensation,
        AssessmentDto assessment,
        PublicationDto publication,
        PositionDocumentDto document
) {}
