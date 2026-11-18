@ApplicationModule(allowedDependencies = {
        "candidate :: api",
        "customcolumn :: api",
        "project :: api",
        "triagecompany :: api"
})
package app.lightmove.api.pairing;

import org.springframework.modulith.ApplicationModule;
