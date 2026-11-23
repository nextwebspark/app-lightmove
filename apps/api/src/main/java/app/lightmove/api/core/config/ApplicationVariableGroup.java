package app.lightmove.api.core.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The headings the platform console lists application variables under, each with the yml branch its keys share. */
@Getter
@RequiredArgsConstructor
public enum ApplicationVariableGroup {

    EXECUTIVE_SOURCING("Executive sourcing", "enrichment.sourcing");

    private final String label;
    private final String keyPrefix;
}
