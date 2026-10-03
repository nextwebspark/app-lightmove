package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.BookingPageKind;

/**
 * What the public booking page opens with. A Nylas page is Nylas's scheduler, opened by its id and API region; a
 * direct one is Uncava's own, read through the link's slots and booked through it. Either way, the consultant's
 * name for the heading and nothing else about them or their firm.
 */
public record BookingPageResponse(BookingPageKind kind, String configurationId, String schedulerApiUrl,
                                  String consultantName, int minutes) {}
