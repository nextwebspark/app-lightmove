package app.lightmove.api.enrichment.company.model;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.Optional;

/** One company page's record, and whether the vendor was asked for it (and so billed) to answer. */
public record CompanyRead(Optional<VendorCompanyRecord> record, boolean bought) {

    public Optional<CapturedCompanyDetails> details() {
        return record.flatMap(VendorCompanyRecord::asCapturedDetails);
    }
}
