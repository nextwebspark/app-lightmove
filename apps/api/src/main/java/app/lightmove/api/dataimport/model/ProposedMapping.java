package app.lightmove.api.dataimport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What the model answers when asked to map a sheet's headers — the structured-output shape Spring AI
 * binds its reply to.
 *
 * <p>Deliberately close to the prompt's own vocabulary rather than to
 * {@link ColumnMapping}: a model given a shape full of nulls-meaning-things answers with nulls
 * meaning other things. Here every entry says plainly what it decided, and translating that into the
 * domain's shape — including refusing anything that does not resolve — happens in
 * {@link app.lightmove.api.dataimport.service.ColumnMappingProposer}, where a bad answer can be
 * dropped rather than trusted.
 *
 * <p>{@code @JsonIgnoreProperties} because a model is free to add a field nobody asked for, and one
 * extra key must not fail the whole mapping.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProposedMapping(List<ProposedColumn> columns) {

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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProposedColumn(
            String header,
            String targetField,
            String customLabel,
            String customTarget,
            String customType
    ) {}
}
