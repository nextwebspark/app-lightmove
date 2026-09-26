package app.lightmove.api.talentmap.dto;

import java.util.Map;
import java.util.UUID;

/**
 * {@link TalentMapResponse}'s points alone, for the poll while {@code geocodingPending} is above zero,
 * so each poll need not re-send thousands of profiles.
 */
public record TalentMapLocationsResponse(Map<UUID, MapLocationDto> locations, int geocodingPending) {}
