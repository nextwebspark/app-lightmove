package app.lightmove.api.notetaker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line of a live call's transcript as Recall streamed it (V154), read by the copilot window after its cursor.
 * Written only by {@code MeetingLiveLineRepository}'s upserts, since a delivery can repeat; {@code partial} is the
 * words-so-far line of a speaker still talking.
 */
@Entity
@Table(name = "app_lm_meeting_live_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingLiveLine {

    @Id
    @Column(name = "seq", nullable = false, updatable = false)
    private long seq;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Column(name = "participant_key", nullable = false, updatable = false)
    private String participantKey;

    @Column(name = "speaker", nullable = false, updatable = false)
    private String speaker;

    @Column(name = "named", nullable = false, updatable = false)
    private boolean named;

    @Column(name = "said_at", nullable = false, updatable = false)
    private Instant saidAt;

    @Column(name = "text", nullable = false, updatable = false)
    private String text;

    @Column(name = "partial", nullable = false, updatable = false)
    private boolean partial;

    public TranscriptLine asTranscriptLine() {
        return new TranscriptLine(saidAt, speaker, text, named);
    }
}
