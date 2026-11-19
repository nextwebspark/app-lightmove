@ApplicationModule(allowedDependencies = {
        "account :: api",
        "billing :: api",
        "candidate :: api",
        "position :: api",
        "project :: api",
        "triagecompany :: api",
        "workspace :: api"
})
package app.lightmove.api.outreach;

import org.springframework.modulith.ApplicationModule;
