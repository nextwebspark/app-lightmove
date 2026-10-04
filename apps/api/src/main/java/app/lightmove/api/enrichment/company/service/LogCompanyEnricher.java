package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** The default: answers nothing, so a fresh clone runs the capture flow with no vendor account. */
@Slf4j
public class LogCompanyEnricher implements LinkedInCompanyEnricher {

    @Override
    public Optional<VendorCompanyRecord> fetch(String linkedinSlug) {
        log.debug("Company enrichment is off — {} stays as captured.", linkedinSlug);
        return Optional.empty();
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
