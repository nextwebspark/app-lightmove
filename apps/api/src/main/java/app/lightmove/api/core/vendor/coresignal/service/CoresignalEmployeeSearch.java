package app.lightmove.api.core.vendor.coresignal.service;

import app.lightmove.api.core.vendor.coresignal.model.CoresignalEmployeeReference;
import app.lightmove.api.core.vendor.model.VendorAttemptResult;
import app.lightmove.api.core.vendor.service.VendorAttemptChain;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Finds the people at one of our companies, trying the precise way to ask before the loose one.
 *
 * <p><b>A separate bean from {@link CoresignalEmployeeClient}, and that is load-bearing.</b>
 * {@code @Retryable} is proxy-based: had this cascade lived on the client and invoked its own
 * annotated methods, every call would have gone straight to the target and every retry would have
 * silently done nothing. It is the trap that already made {@code @Async} inert on
 * {@code AuditService}, and the fix is the same one — {@code AuditService} delegates to
 * {@code AuditEventWriter} for exactly this reason.
 *
 * <p>The split is also the honest shape. Which order to ask in is a matching policy that will change
 * as we learn what resolves Gulf conglomerates well; how to speak HTTP to Coresignal is not.
 */
@RequiredArgsConstructor
public class CoresignalEmployeeSearch {

    private final CoresignalEmployeeClient client;

    /**
     * Who Coresignal has working at this company, asked the precise way first.
     *
     * <p>An identifier we do not hold is simply a step that answers "nobody" — the client spends
     * nothing on a blank one — so a company with only a domain costs exactly one call.
     *
     * @param companyLinkedInUrl the company's own LinkedIn URL, or null if we do not hold one
     * @param companyDomain      its domain, used only when the LinkedIn URL found nobody
     * @return who was found and which lookup found them — the second half being provenance worth
     *         storing, since a hit on an exact entity identifier is better evidence than one on a
     *         domain a whole group shares
     */
    public VendorAttemptResult<List<CoresignalEmployeeReference>> at(String companyLinkedInUrl, String companyDomain) {
        return VendorAttemptChain.<List<CoresignalEmployeeReference>>forLookup("employees at a target company")
                .attempt("linkedin-url", () -> client.atCompanyLinkedInUrl(companyLinkedInUrl))
                .attempt("website", () -> client.atCompanyWebsite(companyDomain))
                .run();
    }
}
