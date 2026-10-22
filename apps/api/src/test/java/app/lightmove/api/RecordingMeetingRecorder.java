package app.lightmove.api;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.service.MeetingRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** A recorder that sends no bot: it remembers every launch and every request to leave. */
public class RecordingMeetingRecorder implements MeetingRecorder {

    private final List<NotetakerLaunch> launched = new CopyOnWriteArrayList<>();
    private final List<String> askedToLeave = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile boolean failLaunches;

    @Override
    public MeetingRecorderKind kind() {
        return MeetingRecorderKind.OWN;
    }

    @Override
    public String launch(NotetakerLaunch launch) {
        if (failLaunches) {
            throw new IllegalStateException("no machine to start");
        }
        launched.add(launch);
        return "bot-" + sequence.incrementAndGet();
    }

    @Override
    public void requestLeave(String externalId) {
        askedToLeave.add(externalId);
    }

    public List<NotetakerLaunch> launched() {
        return List.copyOf(launched);
    }

    public List<String> askedToLeave() {
        return List.copyOf(askedToLeave);
    }

    public void failLaunches(boolean fail) {
        failLaunches = fail;
    }

    public void clear() {
        launched.clear();
        askedToLeave.clear();
        failLaunches = false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingMeetingRecorder recordingMeetingRecorder() {
            return new RecordingMeetingRecorder();
        }
    }
}
