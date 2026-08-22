package app.lightmove.api.project.dto;

/**
 * Where the report's measurement is narrower than the mandate's scope. Every report figure is a
 * stated measurement, so the ways it falls short travel with it rather than being left for the reader
 * to infer from a number that looks lower than it should.
 *
 * <p>Two earlier caveats are gone, and both because the product now has one universe instead of two.
 * The off-limits list used to be unenforceable here — it was keyed on the warehouse's
 * {@code (source, source_id)}, which the report's source had no counterpart for — and selected
 * sectors could be absent from the source entirely, because the two universes drew industries from
 * different vocabularies. The report and the Strategy screen now read the same table, so neither can
 * happen.
 */
public record ScopeCaveatsDto(
        /* True when a revenue band is selected. The universe carries a revenue figure on 7,132 of
         * 71,822 rows, and a company without one cannot be shown to fall in a band — so a revenue-
         * scoped report is measuring a tenth of the market unless Unknown is among the bands. */
        boolean revenueBandExcludesUnknown
) {}
