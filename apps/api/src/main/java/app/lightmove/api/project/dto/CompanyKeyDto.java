package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One company addressed by its rebuild-stable app_lm_companies key — the write shape. */
public record CompanyKeyDto(
        @NotBlank(message = "Every company needs a source")
        @Size(max = 64, message = "That company source is too long")
        String source,

        @NotBlank(message = "Every company needs a source id")
        @Size(max = 256, message = "That company source id is too long")
        String sourceId
) {}
