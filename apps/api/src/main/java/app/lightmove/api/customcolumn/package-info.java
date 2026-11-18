/**
 * <b>Custom column — the columns a mandate added to its own grid.</b> Definitions only; values are a jsonb bag on the
 * row (V46), which is why {@link app.lightmove.api.customcolumn.service.CustomColumnService#applyTo} must guard them.
 * {@code triagecompany} and {@code candidate} depend on this; it depends on neither.
 */
@ApplicationModule(allowedDependencies = {
        "project :: api"
})
package app.lightmove.api.customcolumn;

import org.springframework.modulith.ApplicationModule;
