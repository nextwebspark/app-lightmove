package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * What the header matcher made of a sheet, and whether it had to guess anywhere.
 *
 * <p>{@code everyColumnCertain} is what lets an import skip the model entirely: it is true only when
 * every column was either a known header spelling or an exact match for a column this project already
 * has. A fuzzy match or an unrecognised header makes it false, because those are the two cases the
 * model is worth paying for.
 */
public record HeuristicProposal(List<ColumnMapping> mappings, boolean everyColumnCertain) {

    public HeuristicProposal {
        mappings = List.copyOf(mappings);
    }
}
