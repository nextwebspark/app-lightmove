/**
 * <b>Candidate — the people half of a talent map.</b> A mandate's row maps an executive; its triage company is optional
 * and the employer is snapshotted beside it. Depends on {@code triagecompany}, never the market; nothing depends back.
 */
@ApplicationModule(allowedDependencies = {
        "customcolumn :: api",
        "position :: api",
        "project :: api",
        "triagecompany :: api",
        "workspace :: api"
})
package app.lightmove.api.candidate;

import org.springframework.modulith.ApplicationModule;
