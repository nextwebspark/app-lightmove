package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Snapshot PUT of the off-limits list, as bare universe keys. */
public record PutOffLimitsRequest(
        @NotNull
        @Size(max = 200, message = "That is too many off-limits companies")
        List<@Valid CompanyKeyDto> companies
) {}
