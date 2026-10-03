package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** A time picked on a direct booking page, and who picked it. Nobody is signed in, so nothing here is trusted. */
public record BookOnPageRequest(
        @NotNull Instant startsAt,
        @NotBlank @Size(max = 120) @Pattern(regexp = "[^\\p{Cntrl}]*") String name,
        @NotBlank @Email @Size(max = 320) String email) {}
