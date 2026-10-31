package app.lightmove.api.candidate.service;

import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.FiledCompany;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * One request's filing of employers into a mandate's universe: each distinct employer is resolved against the market
 * once, however many people share it — a bulk Add to position carries up to 500.
 */
@RequiredArgsConstructor
final class EmployerFilings {

    private final TriageCompanyService triage;
    private final UUID projectId;
    private final UUID filedBy;
    private final TriageCompanySource source;
    private final Map<String, TriageCompanyResponse> filed = new HashMap<>();

    /** The mandate's row for the employer, filed if it holds none; only the first person at it is told it was filed. */
    FiledCompany rowFor(CapturedCompanyDetails employer) {
        String key = keyOf(employer);
        TriageCompanyResponse held = filed.get(key);
        if (held != null) {
            return FiledCompany.held(held);
        }
        FiledCompany resolved = triage.fileFromResearch(projectId, filedBy, employer, source);
        filed.put(key, resolved.company());
        return resolved;
    }

    private static String keyOf(CapturedCompanyDetails employer) {
        return employer.companyName().strip().toLowerCase(Locale.ROOT) + "|"
                + Objects.toString(employer.companyLinkedinUrl(), "");
    }
}
