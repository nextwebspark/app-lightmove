package app.lightmove.api.project.model;

/**
 * A mandate named with its client, for a reader outside {@code project} that needs no more.
 * {@code clientApolloAccountId} is the client's row in the company universe, null for a typed client.
 */
public record MandateFacts(String positionTitle, String clientName, String clientSector,
                           String clientHqCountry, String clientHqCity, String clientApolloAccountId) {
}
