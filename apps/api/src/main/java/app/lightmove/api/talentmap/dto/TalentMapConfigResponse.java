package app.lightmove.api.talentmap.dto;

/**
 * Whether this deployment offers the map at all, and the browser token it draws tiles with. The token
 * is a Mapbox <i>public</i> token — meant for a browser, and URL-restricted in the account — served to
 * a signed-in user only because there is no reason to serve it to anyone else.
 */
public record TalentMapConfigResponse(boolean enabled, String publicToken) {}
