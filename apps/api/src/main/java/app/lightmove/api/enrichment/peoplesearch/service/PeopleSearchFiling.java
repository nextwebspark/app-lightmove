package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ResearchedCandidate;
import app.lightmove.api.candidate.model.ResearchedEmployer;
import app.lightmove.api.candidate.model.ResearchedFiling;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.logging.service.MdcPropagation;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.candidate.service.ProfilePhotoDownloader;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails.CompanyFacts;
import app.lightmove.api.enrichment.common.service.ContactOutProfileDetailsReader;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleRequest;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleResponse;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Strategy's People mode, filing: the ticked people into the mandate, each under their employer at the
 * stage asked for, read from the people cache and never the vendor — a search already paid for them.
 */
@Service
@RequiredArgsConstructor
public class PeopleSearchFiling {

    private final CachedContactOutPeopleQuery contactOut;
    private final CandidateService candidates;
    private final ProfilePhotoDownloader photos;
    private final ObjectMapper json;

    /**
     * Photos are fetched together before anything is filed — up to twenty-five downloads one after another
     * would hold the request for their sum.
     */
    public AddPeopleResponse add(UUID userId, UUID workspaceId, UUID projectId, AddPeopleRequest request) {
        TriageCompanyStatus stage = TriageCompanyStatus.parseOrInUniverse(request.status());
        Set<String> held = candidates.mappedProfileSlugsOf(workspaceId, projectId);
        int skipped = 0;
        int unavailable = 0;
        Map<String, BrightDataPerson> toFile = new LinkedHashMap<>();
        for (String slug : new LinkedHashSet<>(request.linkedinSlugs())) {
            String key = slug.toLowerCase(Locale.ROOT);
            if (held.contains(key) || toFile.containsKey(key)) {
                skipped++;
                continue;
            }
            Optional<BrightDataPerson> person = contactOut.onFile(slug);
            if (person.isEmpty()) {
                unavailable++;
            } else {
                toFile.put(key, person.get());
            }
        }

        Map<String, String> sources = contactOut.sourceRecordsOf(toFile.keySet());
        Map<String, EnrichedProfile> research = researchOf(toFile);
        int added = 0;
        int elsewhere = 0;
        List<AddPeopleResponse.Filed> filed = new ArrayList<>();
        for (Map.Entry<String, BrightDataPerson> entry : toFile.entrySet()) {
            Optional<ResearchedCandidate> filing = file(userId, workspaceId, projectId, entry.getValue(),
                    research.get(entry.getKey()), companyOf(sources.get(entry.getKey())), stage);
            if (filing.isEmpty()) {
                skipped++;
                continue;
            }
            added++;
            if (filing.get().employer() != null && !filing.get().employer().status().equals(stage.value())) {
                elsewhere++;
            }
            filed.add(new AddPeopleResponse.Filed(entry.getValue().linkedinId(), filing.get().candidate().id()));
        }
        return new AddPeopleResponse(added, skipped, unavailable, elsewhere, filed);
    }

    private Map<String, EnrichedProfile> researchOf(Map<String, BrightDataPerson> people) {
        try (ExecutorService downloads = Executors.newVirtualThreadPerTaskExecutor()) {
            Executor withRequestContext = MdcPropagation.propagating(downloads);
            Map<String, CompletableFuture<EnrichedProfile>> pending = new LinkedHashMap<>();
            people.forEach((key, person) -> pending.put(key, CompletableFuture.supplyAsync(() ->
                    BrightDataPersonProfiles.toEnrichedProfile(person, EnrichmentVendor.CONTACTOUT)
                            .withPhoto(photos.fetchOrNull(person.usableAvatarUrl())), withRequestContext)));
            Map<String, EnrichedProfile> research = new LinkedHashMap<>();
            pending.forEach((key, profile) -> research.put(key, profile.join()));
            return research;
        }
    }

    /**
     * The employer is filed at {@code stage}, in the person's own write, unless the mandate already holds
     * it, where it stays put and the person joins it there — the Companies screen's rule for a company
     * already triaged. Empty when the mandate already maps the person, found only now by the guards.
     */
    private Optional<ResearchedCandidate> file(UUID userId, UUID workspaceId, UUID projectId, BrightDataPerson person,
                                               EnrichedProfile research, CompanyFacts company,
                                               TriageCompanyStatus stage) {
        ResearchedEmployer employer = research.employerName() == null ? null
                : new ResearchedEmployer(employerDetails(research, company), TriageCompanySource.PEOPLE_SEARCH, stage);
        SaveCandidateRequest filing = SaveCandidateRequest.ofFoundExecutive(null, SearchHitFiling.nameOf(person),
                research.title(), person.profileUrl(), research.locationCountry(), research.locationCity());
        try {
            return Optional.of(candidates.addResearched(userId, workspaceId, projectId, filing, research,
                    ResearchedFiling.ofPeopleSearch(employer)));
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

    private CompanyFacts companyOf(String sourceRecord) {
        return ContactOutProfileDetailsReader.read(sourceRecord, json).map(ContactOutProfileDetails::company)
                .orElse(null);
    }

    /** A company the market does not carry is filed with the facts ContactOut gave about it. */
    private static CapturedCompanyDetails employerDetails(EnrichedProfile research, CompanyFacts company) {
        if (company == null) {
            return new CapturedCompanyDetails(research.employerName(), null, null, null, null, null, null,
                    research.employerLinkedinUrl(), null, null, research.employerLogoUrl(), null, null);
        }
        String website = company.website() != null ? company.website()
                : company.domain() == null ? null : "https://" + company.domain();
        return new CapturedCompanyDetails(research.employerName(), company.industry(), company.country(), null,
                null, null, website, research.employerLinkedinUrl(), company.foundedYear(), null,
                research.employerLogoUrl() != null ? research.employerLogoUrl() : company.logoUrl(), null, null);
    }
}
