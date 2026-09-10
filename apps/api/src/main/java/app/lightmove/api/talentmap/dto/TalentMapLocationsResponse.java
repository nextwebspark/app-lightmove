package app.lightmove.api.talentmap.dto;

import java.util.Map;
import java.util.UUID;

/**
 * The points alone — {@link TalentMapResponse} without the rows they belong to.
 *
 * <p>The screen polls this while {@code geocodingPending} is above zero. A first read of a large
 * import can leave hundreds of places unasked, and every poll would otherwise re-send the whole
 * mandate — thousands of full profiles — to learn where fifty more dots go.
 */
public record TalentMapLocationsResponse(Map<UUID, MapLocationDto> locations, int geocodingPending) {}
