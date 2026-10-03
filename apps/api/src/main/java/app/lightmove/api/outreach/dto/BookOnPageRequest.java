package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** A time picked on a direct booking page, and the address the invite goes to. Nobody is signed in. */
public record BookOnPageRequest(
        @NotNull Instant startsAt,
        @NotBlank @Email @Size(max = 320) String email) {}
