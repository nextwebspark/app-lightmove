package app.lightmove.api.dataimport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The mapping a person confirmed, sent back beside the same file it was proposed for.
 *
 * <p>The confirmed mapping is authoritative: the server does not re-run the proposer at commit time.
 * Re-deciding after a user has corrected a column would silently overrule them, and a model is free
 * to answer differently the second time it is asked the same question.
 */
public record CommitImportRequest(
        @NotNull
        @Valid
        @Size(max = 200, message = "That file has more columns than one import can map")
        List<ProposedColumnMappingDto> columns
) {}
