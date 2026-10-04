package app.lightmove.api.dataimport.model;

import app.lightmove.api.dataimport.constant.MappingSource;
import java.util.List;

/** A mapping for every column, and its source, which the mapping step shows. */
public record ProposedColumnMappings(List<ColumnMapping> mappings, MappingSource source) {

    public ProposedColumnMappings {
        mappings = List.copyOf(mappings);
    }
}
