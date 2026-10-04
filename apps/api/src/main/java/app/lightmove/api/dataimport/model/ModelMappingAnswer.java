package app.lightmove.api.dataimport.model;

import java.util.List;

/** The model's raw, unchecked answer; {@link app.lightmove.api.dataimport.service.ColumnMappingProposer} vets it. */
public record ModelMappingAnswer(List<ModelMappedColumn> columns) {

    /** {@code header} is echoed so answers match by name, not position; all-null means ignore. */
    public record ModelMappedColumn(
            String header,
            String targetField,
            String customLabel,
            String customTarget,
            String customType
    ) {}
}
