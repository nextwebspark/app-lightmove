@ApplicationModule(allowedDependencies = {
        "billing :: api",
        "candidate :: api",
        "enrichment :: api",
        "outreach :: api",
        "position :: api",
        "project :: api",
        "task :: api",
        "workspace :: api"
})
package app.lightmove.api.notetaker;

import org.springframework.modulith.ApplicationModule;
