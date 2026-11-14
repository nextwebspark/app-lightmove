package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PrivacyNoticeChannel;
import app.lightmove.api.core.email.model.EmailMessage;
import java.time.Instant;
import java.util.UUID;

/**
 * One privacy notice recorded as sent and waiting for the commit, with how the person was told before it — what a
 * refused send puts back.
 */
public record QueuedPrivacyNotice(UUID linkId, UUID personId, Instant sentAt, Instant previouslyNotifiedAt,
                                  PrivacyNoticeChannel previousChannel, EmailMessage email) {

    /** The email carries the page's raw token. */
    @Override
    public String toString() {
        return "QueuedPrivacyNotice[link=" + linkId + ", person=" + personId + ", email=<redacted>]";
    }
}
