package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.RecipientEmail;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final ProjectRepository projects;
    private final CandidateService candidateService;
    private final PersonActivityRecorder activity;

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
