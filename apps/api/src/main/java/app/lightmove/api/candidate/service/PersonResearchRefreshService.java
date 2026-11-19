package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.dto.BulkPeopleChangeResponse;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonPhoto;
import app.lightmove.api.candidate.model.PersonResearchRefreshRequested;
import app.lightmove.api.candidate.repository.PersonPhotoRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.text.service.LinkedInUrls;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh research: a person's profile researched again past the people cache, from the drawer or for the people
 * ticked on the Candidates page. The vendor call runs after commit, off the request; its answer lands through
 * {@link #applyRefreshedResearch}, which never overwrites what a researcher typed. Each person is one paid read, so
 * the asking is budgeted per user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonResearchRefreshService {

    private final PersonRepository people;
    private final PersonPhotoRepository photos;
    private final PersonActivityRecorder activity;
    private final PersonVendorRecordStore vendorRecords;
    private final RateLimiter limiter;
    private final ApplicationEventPublisher events;
    private final LightMoveProperties properties;

    @Transactional(readOnly = true)
    public BulkPeopleChangeResponse refreshOne(UUID userId, UUID workspaceId, UUID personId) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        if (person.isProcessingRestricted()) {
            throw ApiException.of(ErrorCode.PERSON_PROCESSING_RESTRICTED);
        }
        return refresh(userId, List.of(person));
    }

    /** Someone restricted, or with no LinkedIn profile to read, is passed over; the answer counts who was sent. */
    @Transactional(readOnly = true)
    public BulkPeopleChangeResponse refreshMany(UUID userId, UUID workspaceId, Collection<UUID> personIds) {
        return refresh(userId, people.findByWorkspaceIdAndIdIn(workspaceId, personIds));
    }

    private BulkPeopleChangeResponse refresh(UUID userId, List<Person> asked) {
        int sent = 0;
        for (Person person : asked) {
            if (person.isProcessingRestricted() || LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl()) == null) {
                continue;
            }
            if (!limiter.tryAcquire("research-refresh:user:%s".formatted(userId),
                    properties.enrichment().researchRefreshesPerUserPerHour(), Duration.ofHours(1))) {
                if (sent == 0) {
                    throw ApiException.of(ErrorCode.RATE_LIMITED);
                }
                break;
            }
            events.publishEvent(new PersonResearchRefreshRequested(person.getWorkspaceId(), person.getId(), userId,
                    person.getLinkedinUrl()));
            sent++;
        }
        return new BulkPeopleChangeResponse(sent);
    }

    /**
     * {@code REQUIRES_NEW}: called from the worker after the asking committed. Skipped when the person went, was
     * restricted, or had their address changed while research ran.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyRefreshedResearch(UUID workspaceId, UUID personId, UUID requestedBy, String researchedUrl,
                                       EnrichedProfile enriched) {
        people.findByIdAndWorkspaceId(personId, workspaceId).ifPresent(person -> {
            String held = LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl());
            if (person.isProcessingRestricted() || held == null
                    || !held.equalsIgnoreCase(LinkedInUrls.profileSlugOrNull(researchedUrl))) {
                log.info("Person {} changed while their research was refreshed; it is not kept", personId);
                return;
            }
            person.refreshResearch(enriched);
            people.flush();
            vendorRecords.keep(workspaceId, personId, enriched.vendorRecords());
            if (enriched.photo() != null && !photos.existsByPersonId(personId)) {
                photos.save(PersonPhoto.of(personId, enriched.photo()));
            }
            activity.record(person, null, requestedBy, PersonActivityKind.RESEARCHED,
                    PersonActivityDetails.of("vendor", enriched.vendor()).and("refreshed", true));
        });
    }
}
