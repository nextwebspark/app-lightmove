package app.lightmove.api.outreach.dto;

/**
 * The Outreach page's tiles. {@code reached} is people at least one email went to; {@code inFlight} is
 * everyone still due an email; {@code booked} is people a call was booked with, which ended their run.
 */
public record OutreachCountsResponse(long enrolled, long emailsSent, long reached, long replied, long inFlight,
                                     long bounced, long stopped, long booked) {}
