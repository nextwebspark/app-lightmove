package app.lightmove.api.companydiscovery.service;

import app.lightmove.api.companydiscovery.constant.DiscoveryMode;
import app.lightmove.api.companydiscovery.model.DiscoveryAnswer;
import app.lightmove.api.companydiscovery.model.DiscoveryQuery;
import lombok.extern.slf4j.Slf4j;

/**
 * The unconfigured deployment. Answers nothing and enables nothing, so the toolbar can disable the
 * CTA rather than offer a button that fails when pressed.
 */
@Slf4j
public class OffCompanyDiscovery implements CompanyDiscovery {

    @Override
    public DiscoveryAnswer discover(DiscoveryQuery query) {
        log.debug("company discovery is not configured; answering nothing");
        return DiscoveryAnswer.none(DiscoveryMode.UNAVAILABLE);
    }

    @Override
    public String provider() {
        return "none";
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
