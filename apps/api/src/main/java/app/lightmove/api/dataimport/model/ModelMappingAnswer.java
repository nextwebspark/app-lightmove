package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * What the model answered when asked to map a sheet's headers — raw and unchecked.
 *
 * <p>Named for where it came from rather than for what it proposes, because
 * {@link ProposedColumnMappings} sits beside it meaning the opposite: this is what a model said, that
 * is what this application decided. Deliberately close to the prompt's own vocabulary too — a model
 * given a shape full of nulls-meaning-things answers with nulls meaning other things. Every entry
 * here says plainly what it decided, and translating that into the domain's shape — including
 * refusing anything that does not resolve — happens in
 * {@link app.lightmove.api.dataimport.service.ColumnMappingProposer}, where a bad answer can be
 * dropped rather than trusted.
 */
public record ModelMappingAnswer(List<ModelMappedColumn> columns) {

    /**
     * One header's verdict.
     *
     * @param header       the header as it appeared in the file, echoed back so a reordered or
     *                     hallucinated answer can be matched to a real column rather than applied by
     *                     position
     * @param targetField  a {@code ImportTargetField} wire token, or null when this is not a built-in
     * @param customLabel  the column name to create when {@code targetField} is null and the column is
     *                     worth keeping; null means ignore the column
     * @param customTarget {@code company} or {@code candidate} — which half of the row a new custom
     *                     column describes
     * @param customType   a {@code CustomColumnType} wire token for a new custom column
     */
    public record ModelMappedColumn(
            String header,
            String targetField,
            String customLabel,
            String customTarget,
            String customType
    ) {}
}
