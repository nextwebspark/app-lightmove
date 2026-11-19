@ApplicationModule(allowedDependencies = {
        "candidate :: api",
        "pairing :: api",
        "project :: api",
        "triagecompany :: api"
})
package app.lightmove.api.publicapi;

import org.springframework.modulith.ApplicationModule;
