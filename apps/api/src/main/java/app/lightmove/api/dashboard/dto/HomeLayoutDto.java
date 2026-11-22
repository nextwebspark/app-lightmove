package app.lightmove.api.dashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * A member's Home: its tiles, which Needs you groups show (by {@code ConversationGroup}), and the version it was read
 * at. A save sends back the version it opened, null when it opened the default.
 */
public record HomeLayoutDto(
        @NotNull @Size(max = 20) List<@Valid @NotNull HomeTileDto> tiles,
        @Size(max = 8) Map<String, Boolean> needs,
        Long version
) {}
