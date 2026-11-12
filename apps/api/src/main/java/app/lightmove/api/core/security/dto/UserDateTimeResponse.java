package app.lightmove.api.core.security.dto;

/** The caller's settled date and time and the layers behind it; {@code own.timeZone} null is Automatic. */
public record UserDateTimeResponse(
        DateTimeChoiceResponse effective,
        DateTimeChoiceResponse own,
        String deviceTimeZone,
        DateTimeChoiceResponse workspace
) {}
