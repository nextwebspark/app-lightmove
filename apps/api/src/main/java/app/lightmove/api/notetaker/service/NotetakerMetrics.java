package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.model.MeetingRecording;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The Notetaker's health by platform and recorder (#956): every bot that got in or did not, and how long it waited to
 * be let in. With {@code notetaker.fallbacks} these are the join success rate, time to admit and fallback rate
 * {@code docs/notetaker.md} charts. Tags are fixed vocabularies — never a run, a person or a call.
 */
@Component
@RequiredArgsConstructor
class NotetakerMetrics {

    static final String JOINS = "notetaker.joins";
    static final String TIME_TO_ADMIT = "notetaker.time_to_admit";

    private final MeterRegistry meters;

    void joined(MeetingRecording recording, Instant at) {
        meters.counter(JOINS, "platform", platformOf(recording), "recorder", recording.getRecorder().name(),
                "outcome", "JOINED").increment();
        if (recording.getLaunchedAt() != null && !at.isBefore(recording.getLaunchedAt())) {
            Timer.builder(TIME_TO_ADMIT)
                    .tag("platform", platformOf(recording))
                    .tag("recorder", recording.getRecorder().name())
                    .publishPercentileHistogram()
                    .register(meters)
                    .record(Duration.between(recording.getLaunchedAt(), at));
        }
    }

    /** A bot that ended without getting in, or that fell over once in; {@code reason} is the run's end reason. */
    void failed(MeetingRecording recording, NotetakerEndReason reason) {
        meters.counter(JOINS, "platform", platformOf(recording), "recorder", recording.getRecorder().name(),
                "outcome", reason.name()).increment();
    }

    private static String platformOf(MeetingRecording recording) {
        return String.valueOf(recording.getPlatform());
    }
}
