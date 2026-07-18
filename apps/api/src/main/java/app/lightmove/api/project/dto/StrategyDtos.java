package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The HTTP contract for the strategy's sector scope. The screen holds the whole selection and PUTs it
 * back as a snapshot (matching the position autosave model), so the three kinds travel split on the
 * wire — direct, adjacent, inferred — and the service flattens them into one ordered list on write.
 */
public final class StrategyDtos {

    private StrategyDtos() {
    }

    /** One chip: a sector or tag label and whether it is currently in scope. */
    public record ChipDto(
            @NotBlank(message = "Name the sector")
            @Size(max = 160, message = "That sector name is too long")
            String label,

            boolean selected
    ) {}

    public record StrategyResponse(
            List<ChipDto> direct,
            List<ChipDto> adjacent,
            List<ChipDto> inferred
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
}
