package app.lightmove.api.outreach.model;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.outreach.constant.OutreachSkipReason;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Whether each person may be approached on a position. One answer for Choose, the openers and Start, so
 * nobody who may not be approached is shown as addable, sent to the model or enrolled.
 */
public record RecipientEligibility(Map<UUID, OutreachEnrollment> liveByPerson) {

    public OutreachSkipReason skipReasonOf(OutreachRecipient recipient) {
        if (recipient.doNotContact()) {
            return OutreachSkipReason.DO_NOT_CONTACT;
        }
        if (recipient.status().hasLeftTheRunning()) {
            return OutreachSkipReason.LEFT_THE_RUNNING;
        }
        if (liveByPerson.containsKey(recipient.personId())) {
            return OutreachSkipReason.ALREADY_IN_SEQUENCE;
        }
        if (recipient.emails().isEmpty()) {
            return OutreachSkipReason.NO_EMAIL;
        }
        return null;
    }

    public boolean anySkipped(Collection<OutreachRecipient> recipients) {
        return recipients.stream().anyMatch(recipient -> skipReasonOf(recipient) != null);
    }

    /** The live enrollment holding this person, when that is why they are skipped. */
    public OutreachEnrollment liveEnrollmentOf(OutreachRecipient recipient) {
        return liveByPerson.get(recipient.personId());
    }
}
