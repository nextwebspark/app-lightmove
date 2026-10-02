package app.lightmove.api;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.constant.RecallCalendarStatus;
import app.lightmove.api.outreach.model.RecallCalendarSpec;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import app.lightmove.api.outreach.service.RecallCalendarApi;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A Recall that keeps the calendars it was handed in memory and remembers every create, update and delete — the
 * lifecycle a direct mailbox's calendar is put through is visible only in the calls made and not made.
 */
public class RecordingRecallCalendarApi implements RecallCalendarApi {

    /** The one signature {@link #updatedCalendars} accepts; anything else is refused as a forged delivery. */
    public static final String VALID_SIGNATURE = "v1,signed-by-recall";

    private final Map<String, RecallCalendarSpec> calendars = new ConcurrentHashMap<>();
    private final Map<String, RecallCalendarStatus> statuses = new ConcurrentHashMap<>();
    private final List<RecallCalendarSpec> createdSpecs = new CopyOnWriteArrayList<>();
    private final List<UpdateRecord> updates = new CopyOnWriteArrayList<>();
    private final List<String> deleted = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile List<String> nextWebhook = List.of();
    private volatile boolean failCreates;

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public String create(RecallCalendarSpec spec) {
        if (failCreates) {
            throw new VendorException(VendorCall.of("recall", "calendar-create"), VendorFailureKind.UNAVAILABLE, null);
        }
        String id = "recall-calendar-" + sequence.incrementAndGet();
        calendars.put(id, spec);
        statuses.put(id, RecallCalendarStatus.CONNECTED);
        createdSpecs.add(spec);
        return id;
    }

    @Override
    public void update(String calendarId, RecallCalendarSpec spec) {
        if (!calendars.containsKey(calendarId)) {
            throw new VendorException(VendorCall.of("recall", "calendar-update"), VendorFailureKind.NOT_FOUND, null);
        }
        calendars.put(calendarId, spec);
        updates.add(new UpdateRecord(calendarId, spec));
    }

    @Override
    public void delete(String calendarId) {
        calendars.remove(calendarId);
        statuses.remove(calendarId);
        deleted.add(calendarId);
    }

    @Override
    public RecallCalendarStatus status(String calendarId) {
        return statuses.getOrDefault(calendarId, RecallCalendarStatus.CONNECTING);
    }

    @Override
    public List<String> updatedCalendars(RecallWebhookDelivery delivery) {
        if (!VALID_SIGNATURE.equals(delivery.signature())) {
            throw ApiException.of(ErrorCode.MAILBOX_WEBHOOK_REJECTED);
        }
        return nextWebhook;
    }

    /** Recall's own refresh of this calendar was refused, as it reports on the next {@code calendar.update}. */
    public void disconnect(String calendarId) {
        statuses.put(calendarId, RecallCalendarStatus.DISCONNECTED);
    }

    public void deliverNext(List<String> updatedCalendarIds) {
        this.nextWebhook = List.copyOf(updatedCalendarIds);
    }

    public void failCreates(boolean fail) {
        this.failCreates = fail;
    }

    public List<RecallCalendarSpec> createdSpecs() {
        return List.copyOf(createdSpecs);
    }

    public List<UpdateRecord> updates() {
        return List.copyOf(updates);
    }

    public List<String> deleted() {
        return List.copyOf(deleted);
    }

    public boolean holds(String calendarId) {
        return calendars.containsKey(calendarId);
    }

    public void clear() {
        calendars.clear();
        statuses.clear();
        createdSpecs.clear();
        updates.clear();
        deleted.clear();
        nextWebhook = List.of();
        failCreates = false;
    }

    public record UpdateRecord(String calendarId, RecallCalendarSpec spec) {}

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the unconfigured API a test profile without a Recall key builds. */
        @Bean
        @Primary
        public RecordingRecallCalendarApi recordingRecallCalendarApi() {
            return new RecordingRecallCalendarApi();
        }
    }
}
