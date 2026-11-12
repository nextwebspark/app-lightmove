package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.text.service.WebsiteDomain;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The In-universe companies a search may take, as the search reads them: page, domain, name, headcount and country. */
@Component
@RequiredArgsConstructor
public class SourcingCompanies {

    private final TriageCompanyReadService companies;
    private final CandidateService candidates;

    /**
     * No ids: the first {@code cap} In-universe companies with nobody mapped, in the grid's own name order. Ids:
     * exactly those, in the order asked, each of which must be In universe here.
     */
    public List<SourcingCompany> chosen(UUID workspaceId, UUID projectId, List<UUID> asked, int cap) {
        List<UUID> distinct = asked == null ? List.of() : List.copyOf(new LinkedHashSet<>(asked));
        if (distinct.size() > cap) {
            throw ApiException.of(ErrorCode.EXECUTIVE_SOURCING_TOO_MANY_COMPANIES);
        }
        return distinct.isEmpty() ? firstWithoutExecutive(workspaceId, projectId, cap)
                : named(workspaceId, projectId, distinct);
    }

    /** The countries the companies are in, by name, so the words come in the languages their titles use. */
    public static List<String> countryNamesOf(List<SourcingCompany> chosen) {
        return chosen.stream()
                .map(SourcingCompany::countryCode)
                .filter(Objects::nonNull)
                .distinct()
                .map(Countries::nameOfCode)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<SourcingCompany> firstWithoutExecutive(UUID workspaceId, UUID projectId, int cap) {
        return companies.firstOfStageExcluding(workspaceId, projectId, TriageCompanyStatus.IN_UNIVERSE,
                        candidates.companiesWithExecutivesOf(workspaceId, projectId), cap).stream()
                .map(SourcingCompanies::toSourcingCompany)
                .toList();
    }

    private List<SourcingCompany> named(UUID workspaceId, UUID projectId, List<UUID> asked) {
        Map<UUID, TriageCompanyResponse> byId = companies.findOfStage(workspaceId, projectId,
                        TriageCompanyStatus.IN_UNIVERSE, Set.copyOf(asked)).stream()
                .collect(Collectors.toMap(TriageCompanyResponse::id, Function.identity()));
        if (byId.size() < asked.size()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "triageCompanyIds",
                    "Only In-universe companies of this position can be searched");
        }
        return asked.stream().map(byId::get).map(SourcingCompanies::toSourcingCompany).toList();
    }

    private static SourcingCompany toSourcingCompany(TriageCompanyResponse company) {
        return new SourcingCompany(company.id(), company.companyName(),
                LinkedInUrls.companySlugOrNull(company.companyLinkedinUrl()), WebsiteDomain.of(company.website()),
                company.numEmployees(), Countries.codeOf(company.companyCountry()));
    }
}
