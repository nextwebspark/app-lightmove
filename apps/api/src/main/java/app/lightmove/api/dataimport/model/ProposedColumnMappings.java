package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * A proposed mapping for every column of a sheet, and whether the model actually produced it.
 *
 * <p>{@code byModel} is not bookkeeping: the mapping step tells the user where the suggestion came
 * from, because the two are worth different amounts of scrutiny. The heuristic matches headers it has
 * seen spellings of before and is confident about nothing else; a run without Application Default
 * Credentials silently gets it every time, and a screen that claimed otherwise would have people
 * trusting a mapping nobody clever made.
 */
public record ProposedColumnMappings(List<ColumnMapping> mappings, boolean byModel) {

    public ProposedColumnMappings {
        mappings = List.copyOf(mappings);
    }
}
