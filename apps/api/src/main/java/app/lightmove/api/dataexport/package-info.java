/**
 * <b>Data export</b> — one whole stage of the Companies grid as a CSV, every drawn and custom column.
 * Reads through {@code triagecompany}, {@code candidate} and {@code customcolumn}, pairing here so
 * {@code triagecompany} never learns people exist; the mirror of {@code dataimport}. Capped by
 * {@code lightmove.export.*}, refusing rather than truncating — a short file looks complete.
 */
@ApplicationModule(allowedDependencies = {
        "candidate :: api",
        "customcolumn :: api",
        "pairing :: api",
        "position :: api",
        "project :: api",
        "triagecompany :: api",
        "workspace :: api"
})
package app.lightmove.api.dataexport;

import org.springframework.modulith.ApplicationModule;
