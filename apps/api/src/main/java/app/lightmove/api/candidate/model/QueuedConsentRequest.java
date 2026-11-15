package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PrivacyNoticeChannel;
import app.lightmove.api.core.email.model.EmailMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One consent request recorded as sent and waiting for the commit, with what a refused send puts back: how the person
 * was told before, and each consent row as it stood.
 */
public record QueuedConsentRequest(UUID linkId, UUID personId, Instant sentAt, Instant previouslyNotifiedAt,
                                   PrivacyNoticeChannel previousChannel, List<ConsentPriorState> priorStates,
                                   EmailMessage email) {

    /** The email carries the page's raw token. */
    @Override
    public String toString() {
        return "QueuedConsentRequest[link=" + linkId + ", person=" + personId + ", email=<redacted>]";
    }
}
