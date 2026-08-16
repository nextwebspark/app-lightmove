package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The selected company-size bands per axis, as range-string values. Capped at each catalog's full
 * size — a request naming more than every band exists is malformed, not a scope. Unknown values are
 * rejected in the service against the {@code EmployeeBand}/{@code RevenueBand} enums.
 */
public record PutCompanySizeRequest(
        @NotNull
        @Size(max = 8, message = "Too many employee bands")
        List<String> employee,

        @NotNull
        @Size(max = 7, message = "Too many revenue bands")
        List<String> revenue
) {}
