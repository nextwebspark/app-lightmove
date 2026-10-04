package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.RecallCalendarStatus;
import app.lightmove.api.outreach.model.RecallCalendarEvent;
import app.lightmove.api.outreach.model.RecallCalendarSpec;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import app.lightmove.api.outreach.model.RecallWebhookNotice;
import java.time.Instant;
import java.util.List;

/** A deployment without Recall: no calendar is ever handed to it, and its webhook is refused. */
public class UnconfiguredRecallCalendarApi implements RecallCalendarApi {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public String create(RecallCalendarSpec spec) {
        throw unavailable();
    }

    @Override
    public void update(String calendarId, RecallCalendarSpec spec) {
        throw unavailable();
    }

    @Override
    public void delete(String calendarId) {
        throw unavailable();
    }

    @Override
    public RecallCalendarStatus status(String calendarId) {
        throw unavailable();
    }

    @Override
    public List<RecallWebhookNotice> notices(RecallWebhookDelivery delivery) {
        throw ApiException.of(ErrorCode.MAILBOX_WEBHOOK_REJECTED);
    }

    @Override
    public List<RecallCalendarEvent> eventsUpdatedSince(String calendarId, Instant since) {
        throw unavailable();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Recall is not configured on this deployment");
    }
}
