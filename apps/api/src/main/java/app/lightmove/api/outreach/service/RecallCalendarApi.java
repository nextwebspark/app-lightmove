package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.RecallCalendarStatus;
import app.lightmove.api.outreach.model.RecallCalendarSpec;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import java.util.List;

/**
 * Recall.ai's Calendar API (v2): one Recall calendar per direct mailbox on a workspace syncing through Recall. Recall
 * holds the refresh token from then on and refreshes it itself. Free, so a failed call is simply made again later.
 */
public interface RecallCalendarApi {

    /** False on a deployment without a Recall key: calendars are then read directly, whatever a workspace chose. */
    boolean isOffered();

    String create(RecallCalendarSpec spec);

    /** Hands an existing calendar a reconnected mailbox's new refresh token. */
    void update(String calendarId, RecallCalendarSpec spec);

    void delete(String calendarId);

    RecallCalendarStatus status(String calendarId);

    /**
     * The calendars a verified delivery says changed state ({@code calendar.update}); other events answer nothing.
     * A delivery whose signature does not verify is refused with {@code MAILBOX_WEBHOOK_REJECTED}.
     */
    List<String> updatedCalendars(RecallWebhookDelivery delivery);
}
