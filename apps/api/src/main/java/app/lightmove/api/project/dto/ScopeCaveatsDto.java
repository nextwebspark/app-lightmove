package app.lightmove.api.project.dto;

import java.util.List;

/**
 * Where the report's source could not answer the mandate's scope in full. Every report figure is a
 * stated measurement, so the ways the measurement is narrower than the scope travel with it rather
 * than being left for the reader to infer from a number that looks lower than it should.
 */
public record ScopeCaveatsDto(
        /* Barred companies that could not be excluded: the off-limits list is keyed on the
         * warehouse's (source, source_id), which this source has no counterpart for. */
        int offLimitsNotApplied,
        /* Selected sectors this source does not carry at all — the difference between "the market
         * is empty" and "we cannot see this part of it". */
        List<String> sectorsNotInSource,
        /* True when a revenue band is selected: this source carries a revenue figure on a minority
         * of rows, and one without a figure cannot be shown to fall in the band. */
        boolean revenueBandExcludesUnknown
) {}
