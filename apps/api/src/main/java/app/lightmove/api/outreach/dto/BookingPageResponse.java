package app.lightmove.api.outreach.dto;

/**
 * What the public booking page opens Nylas's scheduler with: the page's id, the API region it lives in,
 * and the consultant's name for the heading. Nothing else about the consultant or their firm.
 */
public record BookingPageResponse(String configurationId, String schedulerApiUrl, String consultantName) {}
