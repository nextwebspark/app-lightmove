package app.lightmove.api.dashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** A member's Home: its tiles, which Needs you groups show (by {@code ConversationGroup}), and HOME or POSITIONS. */
public record HomeLayoutDto(
        @NotNull @Size(max = 20) List<@Valid @NotNull HomeTileDto> tiles,
        @Size(max = 8) Map<String, Boolean> needs,
        @NotBlank @Size(max = 16) String landing
) {}
