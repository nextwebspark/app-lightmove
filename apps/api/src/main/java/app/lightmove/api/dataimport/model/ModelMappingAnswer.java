package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * What the model answered when asked to map a sheet's headers — raw and unchecked, in the prompt's
 * own vocabulary. {@link ProposedColumnMappings} beside it is what this application decided;
 * translating one into the other, and refusing anything that does not resolve, happens in
 * {@link app.lightmove.api.dataimport.service.ColumnMappingProposer}.
 */
public record ModelMappingAnswer(List<ModelMappedColumn> columns) {

    /**
     * One header's verdict. {@code header} is echoed back so a reordered or hallucinated answer is
     * matched to a real column rather than applied by position; the {@code custom*} fields describe a
     * column to create when {@code targetField} is null, and all-null means ignore the column.
     */
    public record ModelMappedColumn(
            String header,
            String targetField,
            String customLabel,
            String customTarget,
            String customType
    ) {}
}
