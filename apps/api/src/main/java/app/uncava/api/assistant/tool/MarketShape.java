package app.uncava.api.assistant.tool;

import app.uncava.api.strategy.dto.FacetCount;
import app.uncava.api.strategy.dto.SectorGroup;
import app.uncava.api.strategy.model.ScopeBreakdown;
import java.util.List;

/**
 * The vocabulary a market question has to be asked in, with the size of each answer — a tool rather
 * than system-prompt text, so turns that never ask a market question do not carry it.
 */
public record MarketShape(List<SectorGroup> sectors, List<ScopeBreakdown> countries,
                          List<FacetCount> marketSegments, List<FacetCount> employeeBands,
                          List<FacetCount> revenueBands) {
}
