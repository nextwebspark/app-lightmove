package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.dto.FacetCount;
import app.lightmove.api.strategy.dto.SectorGroup;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import java.util.List;

/**
 * The vocabulary a market question has to be asked in, with the size of each answer.
 *
 * <p>The universe carries 148 lower-cased industry labels and spells countries out in full
 * ("United Arab Emirates", never "AE"). A model cannot guess either, and putting both lists in the
 * system prompt would bury a long constant in the cached prefix for the sake of turns that never
 * ask a market question. So it is a tool: asked once, when the model needs to know what it may say.
 *
 * <p>The counts come with it because they answer the follow-up for free — a sector with nine rows
 * and a sector with ten thousand are different suggestions, and the model can tell them apart
 * without a second call.
 */
public record MarketShape(List<SectorGroup> sectors, List<ScopeBreakdown> countries,
                          List<FacetCount> marketSegments, List<FacetCount> employeeBands,
                          List<FacetCount> revenueBands) {
}
