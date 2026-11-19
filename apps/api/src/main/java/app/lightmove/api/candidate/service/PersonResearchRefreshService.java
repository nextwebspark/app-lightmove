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

    private static final Duration ONE_REFRESH_PER = Duration.ofMinutes(10);

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
        if (LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl()) == null) {
            throw ApiException.of(ErrorCode.PERSON_RESEARCH_NEEDS_PROFILE);
        }
        if (!claim(person)) {
            throw ApiException.of(ErrorCode.PERSON_RESEARCH_REFRESHING);
        }
        spendBudget(userId, true);
        publish(userId, person);
        return new BulkPeopleChangeResponse(1);
    }

    /**
     * Someone restricted, with no LinkedIn profile to read, or refreshed in the last few minutes is passed over; the
     * answer counts who was sent.
     */
    @Transactional(readOnly = true)
    public BulkPeopleChangeResponse refreshMany(UUID userId, UUID workspaceId, Collection<UUID> personIds) {
        return refresh(userId, people.findByWorkspaceIdAndIdIn(workspaceId, personIds));
    }

    private BulkPeopleChangeResponse refresh(UUID userId, List<Person> asked) {
        int sent = 0;
        for (Person person : asked) {
            if (person.isProcessingRestricted() || LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl()) == null
                    || !claim(person)) {
                continue;
            }
            if (!spendBudget(userId, sent == 0)) {
                break;
            }
            publish(userId, person);
            sent++;
        }
        return new BulkPeopleChangeResponse(sent);
    }

    /** One paid read per person at a time: a double click, a second tab or an overlapping selection asks nothing. */
    private boolean claim(Person person) {
        return limiter.tryAcquire("research-refresh:person:%s".formatted(person.getId()), 1, ONE_REFRESH_PER);
    }

    private boolean spendBudget(UUID userId, boolean refuseWhenSpent) {
        boolean spent = limiter.tryAcquire("research-refresh:user:%s".formatted(userId),
                properties.enrichment().researchRefreshesPerUserPerHour(), Duration.ofHours(1));
        if (!spent && refuseWhenSpent) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
        return spent;
    }

    private void publish(UUID userId, Person person) {
        events.publishEvent(new PersonResearchRefreshRequested(person.getWorkspaceId(), person.getId(), userId,
                person.getLinkedinUrl()));
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
            vendorRecords.keepFor(person, enriched);
            if (enriched.photo() != null && !photos.existsByPersonId(personId)) {
                photos.save(PersonPhoto.of(personId, enriched.photo()));
            }
            activity.record(person, null, requestedBy, PersonActivityKind.RESEARCHED,
                    PersonActivityDetails.of("vendor", enriched.vendor()).and("refreshed", true));
        });
    }
}
