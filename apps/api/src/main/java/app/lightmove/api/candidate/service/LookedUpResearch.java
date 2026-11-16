package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.EnrichedPhoto;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonPhoto;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonPhotoRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The Add form's LinkedIn lookup on the people side: who may be researched at all, research already held for a
 * profile, and the filing of that research by the save that follows. Research is held thirty minutes per workspace and
 * profile slug, in memory, so the save files what the lookup bought rather than paying for it twice.
 */
@Component
@RequiredArgsConstructor
public class LookedUpResearch implements PersonErasureContributor {

    private static final Duration HELD_FOR = Duration.ofMinutes(30);

    /**
     * Weighed in bytes, not entries: a photo is usually a 256px thumbnail, but one that would not shrink is held whole,
     * up to the downloader's 2 MB.
     */
    private static final long MOST_HELD_BYTES = 32L * 1024 * 1024;
    private static final int ENTRY_OVERHEAD_BYTES = 4 * 1024;

    private final PersonRepository people;
    private final PersonPhotoRepository photos;
    private final CandidateRepository candidates;
    private final PersonEmployerResolver employers;
    private final SuppressionGate suppression;
    private final TriageCompanyService triage;
    private final PersonActivityRecorder activity;
    private final ApplicationEventPublisher events;

    private final Cache<String, EnrichedProfile> held = Caffeine.newBuilder()
            .expireAfterWrite(HELD_FOR)
            .maximumWeight(MOST_HELD_BYTES)
            .weigher((String key, EnrichedProfile research) -> ENTRY_OVERHEAD_BYTES
                    + (research.photo() == null ? 0 : research.photo().content().length))
            .build();

    /**
     * Refuses, before any vendor read, a profile on the workspace's suppression list (an erased or objecting person)
     * and a known person whose processing is restricted: researching them again is itself processing.
     */
    @Transactional(readOnly = true)
    public void refuseUnresearchable(UUID workspaceId, String profileUrl) {
        suppression.refuseSuppressed(workspaceId, profileUrl, List.of());
        Optional<Person> known = people.findByWorkspaceIdAndProfileSlug(workspaceId, slugOf(profileUrl));
        if (known.isPresent()) {
            suppression.refuseSuppressedPerson(workspaceId, known.get());
            if (known.get().isProcessingRestricted()) {
                throw ApiException.of(ErrorCode.PERSON_PROCESSING_RESTRICTED);
            }
        }
    }

    /**
     * Research the workspace already holds for a profile, costing no vendor read: this lookup's earlier answer, or the
     * profile of a person the workspace researched before, filed under the employer it knows them at.
     */
    @Transactional(readOnly = true)
    public Optional<EnrichedProfile> researchOnFile(UUID workspaceId, String profileUrl) {
        String slug = slugOf(profileUrl);
        EnrichedProfile cached = held.getIfPresent(keyOf(workspaceId, slug));
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<EnrichedProfile> known = people.findByWorkspaceIdAndProfileSlug(workspaceId, slug)
                .filter(Person::isResearched)
                .map(person -> researchOf(workspaceId, person));
        known.ifPresent(research -> remember(workspaceId, profileUrl, research));
        return known;
    }

    public void remember(UUID workspaceId, String profileUrl, EnrichedProfile research) {
        String slug = LinkedInUrls.profileSlugOrNull(profileUrl);
        if (slug != null) {
            held.put(keyOf(workspaceId, slug), research);
        }
    }

    /** What a save the lookup answered files, while it is still held. */
    Optional<EnrichedProfile> heldFor(UUID workspaceId, String linkedinUrl) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        return slug == null ? Optional.empty() : Optional.ofNullable(held.getIfPresent(keyOf(workspaceId, slug)));
    }

    /**
     * Research's employer into the mandate's universe — matched to the market row by its LinkedIn page — when the save
     * still names it: a stated employer outranks the vendor's, as research's own mapping holds.
     */
    Optional<TriageCompanyResponse> fileEmployer(UUID projectId, UUID userId, EnrichedProfile research,
                                                 CandidateDetails details) {
        boolean namesResearchedEmployer = research.employerName() != null && details.employerName() != null
                && research.employerName().equalsIgnoreCase(details.employerName().trim());
        if (!namesResearchedEmployer) {
            return Optional.empty();
        }
        return Optional.of(triage.captureFromResearch(projectId, userId, CapturedCompanyDetails.employerNamed(
                research.employerName(), research.employerLinkedinUrl(), research.employerLogoUrl()),
                TriageCompanySource.MANUAL));
    }

    /**
     * The research onto the person filed, then the brief's own assessment, as a capture's. Only onto the profile it was
     * read from: a namesake picked in the possible-duplicate dialog keeps their own profile and gets nothing.
     */
    void fileOnto(UUID userId, UUID workspaceId, UUID projectId, Candidate candidate, EnrichedProfile research,
                  String savedUrl) {
        Person person = candidate.getPerson();
        String savedSlug = LinkedInUrls.profileSlugOrNull(savedUrl);
        if (!Objects.equals(savedSlug, LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl()))) {
            return;
        }
        if (!person.isResearched()) {
            person.enrich(research);
            keepPhoto(person.getId(), research.photo());
            activity.record(candidate, userId, PersonActivityKind.RESEARCHED,
                    PersonActivityDetails.of("vendor", research.vendor()));
        }
        events.publishEvent(new CandidateAiEnrichRequested(candidate.getId(), projectId, workspaceId, userId,
                AiEnrichTrigger.CAPTURE));
        forgetAfterCommit(workspaceId, savedSlug);
    }

    /** An erased person's research goes with them: nothing held may file it back within the window. */
    @Override
    public void erase(ErasedPerson person) {
        if (person.profileSlug() != null) {
            held.invalidate(keyOf(person.workspaceId(), person.profileSlug()));
        }
    }

    private EnrichedProfile researchOf(UUID workspaceId, Person person) {
        List<Candidate> positions = candidates.findPositionsOfPerson(workspaceId, person.getId());
        Optional<CapturedCompanyDetails> employer = employers.knownEmployerOf(workspaceId, person, positions);
        EnrichedPhoto photo = photos.findByPersonId(person.getId())
                .map(stored -> new EnrichedPhoto(stored.getContent(), stored.getContentType()))
                .orElse(null);
        return new EnrichedProfile(person.getTitle(), person.getSummary(),
                employer.map(CapturedCompanyDetails::companyName).orElse(null),
                employer.map(CapturedCompanyDetails::companyLinkedinUrl).orElse(null),
                employer.map(CapturedCompanyDetails::logoUrl).orElse(null),
                person.getLocationCity(), person.getLocationCountry(),
                person.getProfile().career(), person.getProfile().education(), person.getProfile().skills(),
                person.getProfile().languages(), photo, person.getEnrichedBy(), person.getFullName());
    }

    private void keepPhoto(UUID personId, EnrichedPhoto photo) {
        if (photo != null && !photos.existsByPersonId(personId)) {
            photos.save(PersonPhoto.of(personId, photo));
        }
    }

    /** After commit: a save that rolls back (a duplicate refused, say) must find the research still held. */
    private void forgetAfterCommit(UUID workspaceId, String slug) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            held.invalidate(keyOf(workspaceId, slug));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                held.invalidate(keyOf(workspaceId, slug));
            }
        });
    }

    private static String slugOf(String profileUrl) {
        String slug = LinkedInUrls.profileSlugOrNull(profileUrl);
        if (slug == null) {
            throw ApiException.of(ErrorCode.PROFILE_LOOKUP_NOT_A_PROFILE);
        }
        return slug;
    }

    private static String keyOf(UUID workspaceId, String slug) {
        return workspaceId + ":" + slug;
    }
}
