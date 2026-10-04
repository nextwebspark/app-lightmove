package app.lightmove.api.talentmap.dto;

/**
 * Whether the map is offered, and the Mapbox <i>public</i> token it draws tiles with — browser-meant
 * and URL-restricted in the account.
 */
public record TalentMapConfigResponse(boolean enabled, String publicToken) {}
