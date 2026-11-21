package app.lightmove.api.dashboard.dto;

import app.lightmove.api.dashboard.model.ChartChoice;
import java.util.List;

/** An option a library query offers: its key, how the drawer names it, its values and the one it starts at. */
public record ChartOptionDto(String key, String label, List<ChartChoice> choices, String defaultValue) {}
