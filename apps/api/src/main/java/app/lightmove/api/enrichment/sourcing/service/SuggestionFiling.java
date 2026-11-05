package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ResearchedFiling;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.concurrent.service.ThreadContextPropagation;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore.OnFile;
import app.lightmove.api.enrichment.candidate.service.ProfilePhotoDownloader;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.sourcing.dto.AddSuggestionsRequest;
import app.lightmove.api.enrichment.sourcing.dto.AddSuggestionsRequest.PickedPerson;
import app.lightmove.api.enrichment.sourcing.dto.AddSuggestionsResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Add: the people ticked in the panel become executives of the position, each at the company a search found them
 * at, filed from the profile the cache already holds — never a second vendor call. Only someone a search of this
 * position found may be added, so the cache shared by every workspace is never a door into anyone else's people.
 * Each filing is its own transaction, so one refused leaves the others filed.
 */
@Service
public class SuggestionFiling {

    private final SuggestionStore suggestions;
    private final CachedPeopleStore people;
    private final CandidateService candidates;
    private final ProfilePhotoDownloader photos;
    private final AuditService audit;
    private final Duration ttl;

    public SuggestionFiling(SuggestionStore suggestions, CachedPeopleStore people, CandidateService candidates,
                            ProfilePhotoDownloader photos, AuditService audit, LightMoveProperties properties) {
        this.suggestions = suggestions;
        this.people = people;
        this.candidates = candidates;
        this.photos = photos;
        this.audit = audit;
        this.ttl = properties.enrichment().peopleCacheTtl();
    }

    public AddSuggestionsResponse add(UUID userId, UUID workspaceId, UUID projectId, AddSuggestionsRequest request,
                                      HttpServletRequest httpRequest) {
        Instant freshAfter = Instant.now().minus(ttl);
        Set<String> held = candidates.mappedProfileSlugsOf(workspaceId, projectId).stream()
                .map(slug -> slug.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, Picked> picked = new LinkedHashMap<>();
        int alreadyOnPosition = 0;
        int notFound = 0;
        for (PickedPerson person : request.people()) {
            String slug = person.linkedinSlug().strip().toLowerCase(Locale.ROOT);
            if (held.contains(slug)) {
                alreadyOnPosition++;
                continue;
            }
            if (picked.containsKey(slug)) {
                continue;
            }
            Optional<UUID> runId = suggestions.runThatFound(workspaceId, projectId, person.triageCompanyId(), slug,
                    freshAfter);
            if (runId.isEmpty()) {
                notFound++;
            } else {
                picked.put(slug, new Picked(person.triageCompanyId(), runId.get()));
            }
        }

        Map<String, OnFile> read = people.peopleOf(picked.keySet(), freshAfter);
        Map<String, EnrichedProfile> research = researchOf(read);
        int expired = (int) picked.keySet().stream().filter(slug -> !read.containsKey(slug)).count();
        List<AddSuggestionsResponse.Added> filed = new ArrayList<>();
        for (Map.Entry<String, OnFile> entry : read.entrySet()) {
            Picked target = picked.get(entry.getKey());
            BrightDataPerson person = entry.getValue().person();
            Optional<UUID> candidateId = file(userId, workspaceId, projectId, target, person,
                    research.get(entry.getKey()));
            if (candidateId.isEmpty()) {
                alreadyOnPosition++;
            } else {
                filed.add(new AddSuggestionsResponse.Added(person.linkedinId(), target.triageCompanyId(),
                        candidateId.get()));
            }
        }
        if (!filed.isEmpty()) {
            audit.projectEvent(ProjectEventType.EXECUTIVE_SOURCING_ADDED, userId, workspaceId, projectId, httpRequest)
                    .detail("added", filed.size())
                    .detail("companies", filed.stream().map(AddSuggestionsResponse.Added::triageCompanyId).distinct()
                            .count())
                    .record();
        }
        return new AddSuggestionsResponse(filed.size(), alreadyOnPosition, expired, notFound, filed);
    }

    /** Empty when the position already holds the person, found only now by the guards. */
    private Optional<UUID> file(UUID userId, UUID workspaceId, UUID projectId, Picked target, BrightDataPerson person,
                                EnrichedProfile research) {
        SaveCandidateRequest filing = SaveCandidateRequest.ofFoundExecutive(target.triageCompanyId(),
                SearchHitFiling.nameOf(person), research.title(), person.profileUrl(), research.locationCountry(),
                research.locationCity());
        try {
            return Optional.of(candidates.addResearched(userId, workspaceId, projectId, filing, research,
                    ResearchedFiling.ofSourcingRun(target.runId())).candidate().id());
        } catch (ApiException refused) {
            if (refused.getCode() != ErrorCode.CANDIDATE_ALREADY_MAPPED) {
                throw refused;
            }
            return Optional.empty();
        } catch (DataIntegrityViolationException raced) {
            if (!SearchHitFiling.isFilingRace(raced)) {
                throw raced;
            }
            return Optional.empty();
        }
    }

    /**
     * Photos are fetched together before anything is filed — fifty downloads one after another would hold the
     * request for their sum. Each is credited to the index whose record it is.
     */
    private Map<String, EnrichedProfile> researchOf(Map<String, OnFile> read) {
        try (ExecutorService downloads = Executors.newVirtualThreadPerTaskExecutor()) {
            Executor withRequestContext = ThreadContextPropagation.propagating(downloads);
            Map<String, CompletableFuture<EnrichedProfile>> pending = new LinkedHashMap<>();
            read.forEach((slug, onFile) -> pending.put(slug, CompletableFuture.supplyAsync(() ->
                    BrightDataPersonProfiles.toEnrichedProfile(onFile.person(), vendorOf(onFile.provider()))
                            .withPhoto(photos.fetchOrNull(onFile.person().usableAvatarUrl())), withRequestContext)));
            Map<String, EnrichedProfile> research = new LinkedHashMap<>();
            pending.forEach((slug, profile) -> research.put(slug, profile.join()));
            return research;
        }
    }

    private static EnrichmentVendor vendorOf(String provider) {
        return provider.startsWith("brightdata") ? EnrichmentVendor.BRIGHTDATA : EnrichmentVendor.CONTACTOUT;
    }

    private record Picked(UUID triageCompanyId, UUID runId) {}
}
