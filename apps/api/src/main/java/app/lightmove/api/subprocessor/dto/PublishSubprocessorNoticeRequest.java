package app.lightmove.api.subprocessor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A new sub-processor as the console publishes it. The 30 days and the https link are the service's to check. */
public record PublishSubprocessorNoticeRequest(
        @NotBlank @Size(max = 120) String provider,
        @NotBlank @Size(max = 200) String receives,
        @NotBlank @Size(max = 200) String purpose,
        @NotBlank @Size(max = 80) String region,
        @NotNull LocalDate effectiveOn,
        @NotBlank @Size(max = 500) String listUrl
) {
}
