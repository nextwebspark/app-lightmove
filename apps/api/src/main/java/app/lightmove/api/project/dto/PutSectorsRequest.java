package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Snapshot PUT of the sector scope, split by kind as the screen holds it. */
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
