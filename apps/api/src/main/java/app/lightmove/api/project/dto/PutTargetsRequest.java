package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Snapshot PUT of the target-company list, as bare universe keys. */
public record PutTargetsRequest(
        @NotNull
        @Size(max = 200, message = "That is too many target companies")
        List<@Valid CompanyKeyDto> companies
) {}
