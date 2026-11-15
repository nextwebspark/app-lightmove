package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Save as: the held number, as written, to keep with the calling code the server reads for it. */
public record SavePhoneInternationalRequest(
        @NotBlank(message = "Name the number to save")
        @Size(max = 320)
        String number
) {}
