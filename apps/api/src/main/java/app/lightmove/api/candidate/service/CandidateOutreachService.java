package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonEmailKey;
import app.lightmove.api.candidate.model.RecipientEmail;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The seam {@code outreach} reads people through and writes their timeline through. {@code outreach}
 * depends on this; nothing in {@code candidate} knows that outreach exists.
 */
@Service
@RequiredArgsConstructor
public class CandidateOutreachService {

    private final CandidateRepository candidates;
    private final PersonRepository persons;
    private final ProjectRepository projects;
    private final CandidateService candidateService;
    private final PersonActivityRecorder activity;

    /** Where a booked call moves someone from; anyone further on, or out of the running, stays put. */
    private static final Set<CandidateStatus> BEFORE_ENGAGED = EnumSet.of(CandidateStatus.IDENTIFIED,
            CandidateStatus.CONTACTED);

    /** The named executives plus everyone mapped at the named companies, each once, in that order. */
    @Transactional(readOnly = true)
    public List<OutreachRecipient> recipientsOf(UUID workspaceId, UUID projectId, Collection<UUID> candidateIds,
                                                Collection<UUID> triageCompanyIds) {
        projects.requireInWorkspace(projectId, workspaceId);
        Map<UUID, Candidate> found = new LinkedHashMap<>();
        if (!candidateIds.isEmpty()) {
            candidates.findByProjectIdAndIdIn(projectId, candidateIds).forEach(row -> found.put(row.getId(), row));
        }
        if (!triageCompanyIds.isEmpty()) {
            candidates.findByProjectIdAndTriageCompanyIdIn(projectId, triageCompanyIds)
                    .forEach(row -> found.putIfAbsent(row.getId(), row));
        }
        return found.values().stream().map(CandidateOutreachService::recipientOf).toList();
    }

    /** The model's allowlist for one executive, after confirming they belong to this workspace's position. */
    @Transactional(readOnly = true)
    public CandidateDossier dossierOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        candidateService.requireCandidate(workspaceId, projectId, candidateId);
        return candidateService.dossierOf(projectId, candidateId).orElseThrow();
    }

    /** Written in the caller's transaction, beside the enrollment it records. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEnrolled(UUID actor, UUID projectId, UUID candidateId, UUID sequenceId, String sequenceName) {
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        activity.record(candidate, actor, PersonActivityKind.OUTREACH_ENROLLED,
                PersonActivityDetails.of("sequenceId", sequenceId).and("sequence", sequenceName));
    }

    /** Names for people whose mapping is gone: an outreach run outlives the person's removal from the position. */
    @Transactional(readOnly = true)
    public Map<UUID, String> fullNamesOf(UUID workspaceId, Collection<UUID> personIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        persons.findAllById(personIds).stream()
                .filter(person -> person.getWorkspaceId().equals(workspaceId))
                .forEach(person -> names.put(person.getId(), person.getFullName()));
        return names;
    }

    /** One executive as they stand now — what a send re-checks. Empty once they are off the position. */
    @Transactional(readOnly = true)
    public Optional<OutreachRecipient> currentRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.findByIdAndProjectId(candidateId, projectId).map(CandidateOutreachService::recipientOf);
    }

    /**
     * One email went. The first one moves someone still Identified to Contacted — forward only, so a
     * person a consultant already moved on is left where they were put. Nothing is written for someone
     * removed from the position while the email was in flight: the email itself is still recorded.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEmailSent(UUID sender, UUID projectId, UUID candidateId, UUID sequenceId, String sequenceName,
                                int stepNumber) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresent(candidate ->
                recordEmailSent(candidate, sender, sequenceId, sequenceName, stepNumber));
    }

    private void recordEmailSent(Candidate candidate, UUID sender, UUID sequenceId, String sequenceName,
                                 int stepNumber) {
        activity.record(candidate, sender, PersonActivityKind.EMAIL_SENT, PersonActivityDetails
                .of("sequenceId", sequenceId).and("sequence", sequenceName).and("step", stepNumber));
        if (candidate.getStatus() == CandidateStatus.IDENTIFIED) {
            candidate.moveTo(CandidateStatus.CONTACTED);
            activity.record(candidate, sender, PersonActivityKind.STATUS_CHANGED, PersonActivityDetails
                    .of("from", CandidateStatus.IDENTIFIED.value()).and("to", CandidateStatus.CONTACTED.value()));
        }
    }

    /** Nobody here acted, so the line has no actor; the reply's content is never seen, let alone written. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReplied(UUID workspaceId, UUID projectId, UUID personId, UUID sequenceId, String sequenceName) {
        recordAboutPerson(workspaceId, projectId, personId, null, PersonActivityKind.EMAIL_REPLIED,
                PersonActivityDetails.of("sequenceId", sequenceId).and("sequence", sequenceName));
    }

    /** {@code actor} is null when the send-time re-check or a bounce ended the run rather than a person. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStopped(UUID workspaceId, UUID projectId, UUID personId, UUID actor, UUID sequenceId,
                              String sequenceName, String reason) {
        recordAboutPerson(workspaceId, projectId, personId, actor, PersonActivityKind.OUTREACH_STOPPED,
                PersonActivityDetails.of("sequenceId", sequenceId).and("sequence", sequenceName).and("reason", reason));
    }

    /**
     * The workspace's people holding each of these addresses, keyed by {@link #emailKeyOf} — a calendar event
     * is kept only for them. One query for a whole calendar's worth of addresses.
     */
    @Transactional(readOnly = true)
    public Map<String, Set<UUID>> personIdsByEmailKey(UUID workspaceId, Collection<String> addresses) {
        Set<String> keys = addresses.stream()
                .filter(address -> address != null && !address.isBlank())
                .map(CandidateOutreachService::emailKeyOf)
                .collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Map.of();
        }
        return persons.findHoldersByWorkspaceIdAndEmailKeyIn(workspaceId, keys).stream()
                .collect(Collectors.groupingBy(PersonEmailKey::getEmailKey,
                        Collectors.mapping(PersonEmailKey::getPersonId, Collectors.toSet())));
    }

    /** The ledger's key for an address, so a differently cased address on an invite still finds its person. */
    public static String emailKeyOf(String address) {
        return CandidateContact.keyOf(ContactChannel.EMAIL, address);
    }

    /**
     * A call was booked. It moves someone still Identified or Contacted to Engaged — forward only, so a
     * person a consultant already moved on, or out of the running, is left where they were put.
     * {@code viaLink} says the executive booked it themselves.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMeetingBooked(UUID actor, UUID projectId, UUID candidateId, Instant startsAt, boolean viaLink) {
        candidates.findByIdAndProjectId(candidateId, projectId).ifPresent(candidate -> {
            activity.record(candidate, actor, PersonActivityKind.MEETING_BOOKED, PersonActivityDetails
                    .of("startsAt", startsAt.toString()).and("viaLink", viaLink));
            CandidateStatus was = candidate.getStatus();
            if (BEFORE_ENGAGED.contains(was)) {
                candidate.moveTo(CandidateStatus.ENGAGED);
                activity.record(candidate, actor, PersonActivityKind.STATUS_CHANGED, PersonActivityDetails
                        .of("from", was.value()).and("to", CandidateStatus.ENGAGED.value()));
            }
        });
    }

    /** By person, not by mapping: someone removed from the position since they were enrolled can still reply. */
    private void recordAboutPerson(UUID workspaceId, UUID projectId, UUID personId, UUID actor,
                                   PersonActivityKind kind, PersonActivityDetails details) {
        persons.findByIdAndWorkspaceId(personId, workspaceId)
                .ifPresent(person -> activity.record(person, projectId, actor, kind, details));
    }

    private static OutreachRecipient recipientOf(Candidate candidate) {
        Person person = candidate.getPerson();
        List<RecipientEmail> emails = person.emailContacts().stream()
                .map(CandidateOutreachService::emailOf)
                .toList();
        return new OutreachRecipient(candidate.getId(), person.getId(), candidate.getTriageCompanyId(),
                person.getFullName(), person.getTitle(), candidate.getCompanyName(), person.getLocationCity(),
                person.getLocationCountry(), candidate.getStatus(), person.isDoNotContact(), emails);
    }

    private static RecipientEmail emailOf(CandidateContact contact) {
        return new RecipientEmail(contact.getValue(), contact.getKind(), contact.isVerified());
    }
}
