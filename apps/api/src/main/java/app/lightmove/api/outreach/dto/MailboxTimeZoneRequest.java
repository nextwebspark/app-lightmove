package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The IANA zone a consultant's sending hours are read in, such as {@code Europe/London}. */
public record MailboxTimeZoneRequest(
        @NotBlank(message = "Choose a time zone")
        @Size(max = 64, message = "Choose a time zone")
        String timeZone) {}
