package app.lightmove.api.project.model;

/** A mandate named with its client, for a reader outside {@code project} that needs no more. */
public record MandateFacts(String positionTitle, String clientName, String clientSector,
                           String clientHqCountry, String clientHqCity) {
}
