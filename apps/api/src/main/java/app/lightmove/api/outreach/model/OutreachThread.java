package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import java.util.UUID;

/**
 * A thread one consultant's mailbox wrote into, and the conversation it belongs to: who on which position, through
 * which run if a run wrote it. A thread id is unique only within one mailbox, so the sender is part of its identity.
 */
public record OutreachThread(UUID workspaceId, UUID senderUserId, String threadId, MailboxGatewayKind gateway,
                             UUID projectId, UUID personId, UUID candidateId, UUID enrollmentId, String sentFrom) {

    public static OutreachThread of(OutreachMessage sent) {
        return new OutreachThread(sent.getWorkspaceId(), sent.getSenderUserId(), sent.getThreadId(),
                sent.getThreadGateway(), sent.getProjectId(), sent.getPersonId(), sent.getCandidateId(),
                sent.getEnrollmentId(), sent.getFromAddress());
    }

    /** A run whose email went but could not be recorded leaves no line; its thread is still its own. */
    public static OutreachThread of(OutreachEnrollment run) {
        return new OutreachThread(run.getWorkspaceId(), run.getSenderUserId(), run.getThreadId(),
                run.getThreadGateway(), run.getProjectId(), run.getPersonId(), run.getCandidateId(), run.getId(),
                null);
    }

    /**
     * A thread another gateway made is not this mailbox's to read, as a run's is not (V110), nor one sent from
     * another address: the consultant reconnected to another account, which holds no such thread.
     */
    public boolean isReadableBy(MailboxConnection mailbox) {
        return (gateway == null || gateway == mailbox.getGateway())
                && (sentFrom == null || sentFrom.equalsIgnoreCase(mailbox.getAddress()));
    }
}
