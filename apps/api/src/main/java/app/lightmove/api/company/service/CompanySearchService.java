package app.lightmove.api.company.service;

import app.lightmove.api.company.dto.CompanyDtos.Company;
import app.lightmove.api.company.dto.CompanyDtos.CompanySearchFilter;
import app.lightmove.api.company.dto.CompanyDtos.CompanySearchResponse;
import app.lightmove.api.company.dto.CompanyDtos.TaxonomyMatch;
import app.lightmove.api.company.model.CompanySearchCommand;
import app.lightmove.api.company.repository.CompanyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a company search: embed the query, shortlist taxonomy candidates by vector similarity,
 * resolve them (plus any country/size/revenue filters) via one LLM call, then run the resulting
 * parameterized query against {@code app_lm_companies}.
 */
@Service
@RequiredArgsConstructor
public class CompanySearchService {

    /** How many taxonomy candidates the vector-search step shortlists for the LLM to choose among. */
    private static final int TAXONOMY_CANDIDATE_LIMIT = 30;

    private final TaxonomySearchService taxonomySearch;
    private final CompanyIntentExtractor intentExtractor;
    private final CompanyRepository companies;

    public CompanySearchResponse search(CompanySearchCommand command) {
        List<TaxonomyMatch> candidates = taxonomySearch.findCandidates(command.query(), TAXONOMY_CANDIDATE_LIMIT);
        CompanySearchFilter filter = intentExtractor.extract(command.query(), candidates);

        // No taxonomy match at all: fail closed rather than falling back to an unfiltered scan of all
        // 54k rows. resolvedFilter and candidates are still populated so the caller can see why.
        if (filter.primaryTypes().isEmpty() && filter.tags().isEmpty()) {
            return new CompanySearchResponse(filter, candidates, List.of(), 0, command.page(), command.size());
        }

        Page<Company> page = companies.search(filter, command.page(), command.size());
        return new CompanySearchResponse(
                filter, candidates, page.getContent(), page.getTotalElements(), command.page(), command.size());
    }
}
