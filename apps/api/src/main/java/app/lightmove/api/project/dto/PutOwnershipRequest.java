package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The selected ownership structures as org_type values ("Privately Held", "Public Company").
 * Capped at the catalog's full size; unknown values are rejected in the service against the
 * {@code OwnershipStructure} enum.
 */
public record PutOwnershipRequest(
        @NotNull
        @Size(max = 8, message = "Too many ownership structures")
        List<String> structures
) {}
