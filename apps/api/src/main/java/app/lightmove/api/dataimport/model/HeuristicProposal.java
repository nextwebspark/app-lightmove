package app.lightmove.api.dataimport.model;

import java.util.List;

/** {@code everyColumnCertain} skips the model: no fuzzy match and no unrecognised header anywhere. */
public record HeuristicProposal(List<ColumnMapping> mappings, boolean everyColumnCertain) {

    public HeuristicProposal {
        mappings = List.copyOf(mappings);
    }
}
