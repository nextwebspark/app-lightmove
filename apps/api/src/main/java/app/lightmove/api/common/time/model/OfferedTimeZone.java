package app.lightmove.api.common.time.model;

/** One row of the fixed time zone list: the IANA id and the cities it is offered as. */
public record OfferedTimeZone(String zone, String label) {}
