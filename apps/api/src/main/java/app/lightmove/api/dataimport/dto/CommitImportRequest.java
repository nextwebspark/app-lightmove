package app.lightmove.api.dataimport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The confirmed mapping is authoritative: re-running the proposer would silently overrule the user. */
public record CommitImportRequest(
        @NotNull
        @Size(max = 200, message = "That file has more columns than one import can map")
        List<@Valid ProposedColumnMappingDto> columns
) {}
