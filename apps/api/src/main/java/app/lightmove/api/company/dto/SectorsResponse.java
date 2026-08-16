package app.lightmove.api.company.dto;

import java.util.List;

/** Every distinct sector in the universe, with counts. */
public record SectorsResponse(List<SectorCount> sectors) {}
