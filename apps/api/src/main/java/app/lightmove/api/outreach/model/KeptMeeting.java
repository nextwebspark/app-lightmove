package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A meeting with a person as another feature reads it: the call, the link to join it, whose calendar holds this copy
 * of it and who booked it in Uncava, if anyone did.
 */
public record KeptMeeting(UUID id, UUID personId, String title, Instant startsAt, Instant endsAt, String joinUrl,
                          UUID holderUserId, UUID bookedByUserId) {

    /** Every teammate's calendar keeps its own copy of a call; this is how {@code MeetingService} tells them apart. */
    public boolean isSameCallAs(KeptMeeting other) {
        return startsAt.equals(other.startsAt) && endsAt.equals(other.endsAt)
                && titleKey().equals(other.titleKey());
    }

    public boolean isHeldOrBookedBy(UUID userId) {
        return userId.equals(holderUserId) || userId.equals(bookedByUserId);
    }

    private String titleKey() {
        return Objects.toString(title, "").trim().toLowerCase(Locale.ROOT);
    }
}
