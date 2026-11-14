package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The version the re-acceptance sheet showed, so a tick is never recorded against terms the person did not read. */
public record AcceptTermsRequest(
        @NotBlank(message = "Missing terms version")
        @Size(max = 32)
        String version
) {}
