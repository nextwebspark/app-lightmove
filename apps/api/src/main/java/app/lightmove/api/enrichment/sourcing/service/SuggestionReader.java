package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.candidate.service.SuppressionGate;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore.OnFile;
import app.lightmove.api.enrichment.peoplesearch.dto.PersonResultDto;
import app.lightmove.api.enrichment.sourcing.constant.SourcingOutcome;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionBand;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSuggestionsResponse;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSuggestionsResponse.CompanySuggestions;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSuggestionsResponse.SuggestedPerson;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCompanyOutcomeResponse;
import app.lightmove.api.enrichment.sourcing.model.CompanyOutcome;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.Suggestion;
import app.lightmove.api.enrichment.sourcing.repository.ExecutiveSourcingRunRepository;
import app.lightmove.api.enrichment.sourcing.service.SuggestionStore.StoredSuggestion;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each company's latest search as the panel reviews it: the people in rank order, each read from the people cache
 * — never a vendor — and marked when the position already holds them. A company's results last as long as the cache
 * keeps who they were. Someone put on the suppression list since the search is left out of it.
 */
@Service
public class SuggestionReader {

    /** Results a search left: those that searched the company; filing runs from before the panel left none. */
    private static final Set<SourcingOutcome> SEARCHED = Set.of(SourcingOutcome.FOUND, SourcingOutcome.NOTHING_FIT,
            SourcingOutcome.NO_HITS);

    private final ExecutiveSourcingRunRepository runs;
    private final SuggestionStore suggestions;
    private final CachedPeopleStore people;
    private final CandidateService candidates;
    private final SuppressionGate suppression;
    private final Duration ttl;

    public SuggestionReader(ExecutiveSourcingRunRepository runs, SuggestionStore suggestions, CachedPeopleStore people,
                            CandidateService candidates, SuppressionGate suppression, LightMoveProperties properties) {
        this.runs = runs;
        this.suggestions = suggestions;
        this.people = people;
        this.candidates = candidates;
        this.suppression = suppression;
        this.ttl = properties.enrichment().peopleCacheTtl();
    }

    /** {@code companyIds} empty reads every company searched within the cache's life. */
    @Transactional(readOnly = true)
    public ExecutiveSuggestionsResponse read(UUID workspaceId, UUID projectId, Collection<UUID> companyIds) {
        Instant freshAfter = Instant.now().minus(ttl);
        Map<UUID, Latest> latest = latestSearchOf(workspaceId, projectId, companyIds, freshAfter);
        Map<UUID, List<Suggestion>> found = suggestions.foundBy(workspaceId, projectId,
                        latest.values().stream().map(Latest::runId).collect(Collectors.toSet()), latest.keySet())
                .stream()
                .filter(stored -> stored.runId().equals(latest.get(stored.triageCompanyId()).runId()))
                .collect(Collectors.groupingBy(StoredSuggestion::triageCompanyId, LinkedHashMap::new,
                        Collectors.mapping(StoredSuggestion::suggestion, Collectors.toList())));
        Set<String> slugs = found.values().stream()
                .flatMap(List::stream)
                .map(Suggestion::linkedinSlug)
                .collect(Collectors.toSet());
        Set<String> suppressed = suppression.suppressedProfileSlugsOf(workspaceId, slugs);
        Map<String, OnFile> read = people.peopleOf(slugs, freshAfter);
        Map<String, UUID> onPosition = new LinkedHashMap<>();
        candidates.mappedProfileCandidatesOf(workspaceId, projectId)
                .forEach((slug, candidateId) -> onPosition.putIfAbsent(slug.toLowerCase(Locale.ROOT), candidateId));
        List<CompanySuggestions> companies = new ArrayList<>();
        latest.forEach((companyId, search) -> {
            List<Suggestion> ranked = found.getOrDefault(companyId, List.of());
            List<SuggestedPerson> listed = ranked.stream()
                    .filter(suggestion -> !suppressed.contains(suggestion.linkedinSlug()))
                    .map(suggestion -> suggestedPerson(suggestion, read, onPosition))
                    .toList();
            boolean expired = !listed.isEmpty() && listed.stream().allMatch(person -> person.person() == null);
            companies.add(new CompanySuggestions(companyId, search.outcome().companyName(), search.runId(),
                    search.searchedAt(), SourcingCompanyOutcomeResponse.of(search.outcome()), expired,
                    listed.stream().filter(person -> person.person() != null).toList()));
        });
        return new ExecutiveSuggestionsResponse(companies);
    }

    /** The latest run that searched each company, newest first, within the cache's life. */
    private Map<UUID, Latest> latestSearchOf(UUID workspaceId, UUID projectId, Collection<UUID> companyIds,
                                             Instant freshAfter) {
        Set<UUID> wanted = Set.copyOf(companyIds);
        Map<UUID, Latest> latest = new LinkedHashMap<>();
        for (ExecutiveSourcingRun run : runs.findByWorkspaceIdAndProjectIdAndCreatedAtAfterOrderByCreatedAtDesc(
                workspaceId, projectId, freshAfter)) {
            for (CompanyOutcome outcome : run.getOutcomes()) {
                if (SEARCHED.contains(outcome.outcome())
                        && (wanted.isEmpty() || wanted.contains(outcome.triageCompanyId()))) {
                    latest.putIfAbsent(outcome.triageCompanyId(),
                            new Latest(run.getId(), run.getCreatedAt(), outcome));
                }
            }
        }
        return latest;
    }

    private static SuggestedPerson suggestedPerson(Suggestion suggestion, Map<String, OnFile> read,
                                                   Map<String, UUID> onPosition) {
        String slug = suggestion.linkedinSlug().toLowerCase(Locale.ROOT);
        BrightDataPerson person = read.containsKey(slug) ? read.get(slug).person() : null;
        UUID candidateId = onPosition.get(slug);
        return new SuggestedPerson(suggestion.linkedinSlug(), suggestion.verdict(), suggestion.score(),
                suggestion.score() == null ? null : SuggestionBand.of(suggestion.score()), suggestion.rank(),
                suggestion.reasons(), suggestion.foundBy(), candidateId,
                person == null ? null : PersonResultDto.of(person, null, candidateId));
    }

    private record Latest(UUID runId, Instant searchedAt, CompanyOutcome outcome) {}
}
