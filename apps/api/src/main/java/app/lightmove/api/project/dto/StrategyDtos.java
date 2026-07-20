package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The HTTP contract for the strategy's scope sections. The screen holds each section's whole selection
 * and PUTs it back as a snapshot (matching the position autosave model): sectors travel split by kind
 * — direct, adjacent, inferred — and the service flattens them into one ordered list on write; the
 * fixed-catalog sections (company size, geography, ownership) travel as selected values only.
 */
public final class StrategyDtos {

    private StrategyDtos() {
    }

    /** One chip: a sector or tag label and whether it is currently in scope. */
    public record ChipDto(
            @NotBlank(message = "Every chip needs a label")
            @Size(max = 160, message = "That chip label is too long")
            String label,

            boolean selected
    ) {}

    public record StrategyResponse(
            List<ChipDto> direct,
            List<ChipDto> adjacent,
            List<ChipDto> inferred,
            // The company-size scope carries only the *selected* band values per axis (the range strings,
            // e.g. "51-200" / "5M-25M"); the client renders the full catalog from its own mirror and marks
            // these in scope. Empty lists mean nothing selected, not "no such axis".
            List<String> employee,
            List<String> revenue,
            // Geography and ownership follow the same selected-values-only model. markets carries ISO
            // country codes ("AE", "SA"), structures carries stable tokens ("PUBLICLY_LISTED") — the
            // client's catalog mirror owns the display names.
            List<String> markets,
            List<String> structures
    ) {}

    public record PutSectorsRequest(
            @NotNull
            @Size(max = 15, message = "That is too many direct sectors")
            List<@Valid ChipDto> direct,

            // Ceilings above the client-side caps (20 adjacent / 15 inferred): the UI keeps the list
            // trim by dropping deselected suggestions, but a selection-heavy scope must still save.
            @NotNull
            @Size(max = 40, message = "That is too many adjacent sectors")
            List<@Valid ChipDto> adjacent,

            @NotNull
            @Size(max = 30, message = "That is too many inferred tags")
            List<@Valid ChipDto> inferred
    ) {}

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

    /**
     * The selected geography markets as ISO country codes. Capped at the catalog's full size; unknown
     * values are rejected in the service against the {@code GeographyMarket} enum.
     */
    public record PutGeographyRequest(
            @NotNull
            @Size(max = 6, message = "Too many markets")
            List<String> markets
    ) {}

    /**
     * The selected ownership structures as stable tokens. Capped at the catalog's full size; unknown
     * values are rejected in the service against the {@code OwnershipStructure} enum.
     */
    public record PutOwnershipRequest(
            @NotNull
            @Size(max = 5, message = "Too many ownership structures")
            List<String> structures
    ) {}
}
