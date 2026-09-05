package app.lightmove.api.dataimport.model;

import app.lightmove.api.dataimport.constant.MappingSource;
import java.util.List;

/**
 * A proposed mapping for every column of a sheet, and what produced it.
 *
 * <p>The source is not bookkeeping: the mapping step tells the user where the suggestion came from,
 * because the three are worth different amounts of scrutiny. A sheet whose headers all matched by name
 * needs a glance; one the header matcher guessed at needs reading.
 */
public record ProposedColumnMappings(List<ColumnMapping> mappings, MappingSource source) {

    public ProposedColumnMappings {
        mappings = List.copyOf(mappings);
    }
}
