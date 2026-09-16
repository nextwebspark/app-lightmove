package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One email or phone as the drawer or the Add form sends it. {@code kind} is {@code "work"},
 * {@code "personal"} or absent; {@code verified} is the person's own claim, recorded as such.
 */
public record ContactEntryDto(
        @NotBlank(message = "A contact needs a value")
        @Size(max = 320)
        String value,

        @Size(max = 16)
        String kind,

        /** Absent reads as false: the Add form and the plugin do not claim what they do not know. */
        Boolean verified
) {}
