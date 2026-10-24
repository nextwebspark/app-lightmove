package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.model.LiveLinesHeard;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.RecallLiveUtterance;
import app.lightmove.api.notetaker.repository.MeetingLiveLineRepository;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recall's real-time transcript for a live copilot, line by line as it is spoken. A delivery counts only with the token
 * in its bot's own URL, or a Svix signature under the bot webhook secret; a token feeds its own recording and no other.
 * Only a bot we hold, still in its call with the copilot listening, takes lines; anything else is dropped, so a stopped
 * copilot keeps nothing more of the call. A delivery answers quickly: the coach runs after the commit, off this thread.
 */
@Service
@RequiredArgsConstructor
public class NotetakerLiveFeed {

    private static final int LONGEST_SPEAKER = 200;
    private static final int LONGEST_LINE = 4_000;
    /** Words so far arrive several times a second per voice; windows are told of them at most this often. */
    static final Duration PARTIAL_ANNOUNCE_EVERY = Duration.ofMillis(500);
    private static final int MOST_ANNOUNCED_HELD = 1_000;

    private final Map<UUID, Instant> partialAnnounced = new ConcurrentHashMap<>();

    private final RecallBotApi recall;
    private final MeetingRecordingRepository recordings;
    private final MeetingLiveLineRepository lines;
    private final CopilotStreamPublisher publisher;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public void receive(byte[] body, String token, String id, String timestamp, String signature) {
        Instant received = clock.instant();
        boolean signed = recall.verifiesLive(body, id, timestamp, signature);
        if (!signed && (token == null || token.isBlank())) {
            throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
        }
        List<RecallLiveUtterance> utterances = RecallBotClient.liveUtterancesIn(body);
        for (RecallLiveUtterance utterance : utterances) {
            Boolean taken = transactions.execute(status -> {
                MeetingRecording recording = recordings.findByRecallBotId(utterance.botId()).orElse(null);
                if (recording == null) {
                    return signed;
                }
                if (!signed && !recording.holdsLiveToken(token)) {
                    return false;
                }
                if (recording.isListening()) {
                    take(recording, utterance, received);
                }
                return true;
            });
            if (!Boolean.TRUE.equals(taken)) {
                throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
            }
        }
    }

    private void take(MeetingRecording recording, RecallLiveUtterance utterance, Instant received) {
        String speaker = utterance.speaker() == null ? "Speaker " + utterance.participantKey()
                : clip(utterance.speaker(), LONGEST_SPEAKER);
        boolean named = utterance.speaker() != null;
        Instant saidAt = saidAtOf(utterance, received);
        String participant = clip(utterance.participantKey(), 80);
        String text = clip(utterance.text(), LONGEST_LINE);
        if (utterance.partial()) {
            lines.upsertPartial(recording.getId(), "partial:" + participant, participant, speaker, named, saidAt,
                    text);
        } else {
            int inserted = lines.insertFinished(recording.getId(), utteranceKeyOf(utterance), participant, speaker,
                    named, saidAt, text);
            lines.clearPartial(recording.getId(), participant);
            if (inserted > 0) {
                events.publishEvent(new LiveLinesHeard(recording.getId()));
            }
        }
        if (!utterance.partial() || isDueToAnnounce(recording.getId(), received)) {
            publisher.publish(recording.getId());
        }
    }

    private boolean isDueToAnnounce(UUID recordingId, Instant now) {
        if (partialAnnounced.size() > MOST_ANNOUNCED_HELD) {
            partialAnnounced.values().removeIf(at -> at.isBefore(now.minus(Duration.ofMinutes(1))));
        }
        Instant last = partialAnnounced.get(recordingId);
        if (last != null && last.plus(PARTIAL_ANNOUNCE_EVERY).isAfter(now)) {
            return false;
        }
        partialAnnounced.put(recordingId, now);
        return true;
    }

    /**
     * Recall's real-time words carry offsets into the recording, not clock times; a line is placed at the time it
     * arrived, less how long it took to say, unless Recall sent an absolute time.
     */
    static Instant saidAtOf(RecallLiveUtterance utterance, Instant received) {
        if (utterance.startedAt() != null) {
            return utterance.startedAt();
        }
        if (utterance.startSeconds() != null && utterance.endSeconds() != null
                && utterance.endSeconds() >= utterance.startSeconds()) {
            return received.minus(Duration.ofMillis(Math.round((utterance.endSeconds() - utterance.startSeconds())
                    * 1000)));
        }
        return received;
    }

    /** The same utterance delivered twice — a Svix retry — keys the same: its speaker and its first word's offset. */
    static String utteranceKeyOf(RecallLiveUtterance utterance) {
        String start = utterance.startSeconds() != null ? String.format(Locale.ROOT, "%.3f", utterance.startSeconds())
                : utterance.startedAt() != null ? utterance.startedAt().toString()
                : Integer.toHexString(utterance.text().hashCode());
        return clip(utterance.participantKey(), 80) + ":" + start;
    }

    private static String clip(String value, int longest) {
        return value.length() <= longest ? value : value.substring(0, longest);
    }
}
