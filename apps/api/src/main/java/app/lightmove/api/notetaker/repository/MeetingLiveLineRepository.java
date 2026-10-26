package app.lightmove.api.notetaker.repository;

import app.lightmove.api.notetaker.model.MeetingLiveLine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * A live call's streamed transcript. Recall may deliver an utterance more than once, so a finished line is inserted
 * once on its utterance key, and a speaker's words-so-far line is replaced in place.
 */
public interface MeetingLiveLineRepository extends JpaRepository<MeetingLiveLine, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO app_lm_meeting_live_line
                (recording_id, utterance_key, participant_key, speaker, named, said_at, text, partial)
            VALUES (:recordingId, :utteranceKey, :participantKey, :speaker, :named, :saidAt, :text, false)
            ON CONFLICT (recording_id, utterance_key) WHERE NOT partial DO NOTHING
            """)
    int insertFinished(UUID recordingId, String utteranceKey, String participantKey, String speaker, boolean named,
                       Instant saidAt, String text);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO app_lm_meeting_live_line
                (recording_id, utterance_key, participant_key, speaker, named, said_at, text, partial)
            VALUES (:recordingId, :utteranceKey, :participantKey, :speaker, :named, :saidAt, :text, true)
            ON CONFLICT (recording_id, participant_key) WHERE partial
            DO UPDATE SET text = EXCLUDED.text, said_at = EXCLUDED.said_at, speaker = EXCLUDED.speaker,
                          utterance_key = EXCLUDED.utterance_key
            """)
    int upsertPartial(UUID recordingId, String utteranceKey, String participantKey, String speaker, boolean named,
                      Instant saidAt, String text);

    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM app_lm_meeting_live_line
            WHERE recording_id = :recordingId AND participant_key = :participantKey AND partial
            """)
    int clearPartial(UUID recordingId, String participantKey);

    List<MeetingLiveLine> findByRecordingIdAndPartialFalseAndSeqGreaterThanOrderBySeqAsc(UUID recordingId,
                                                                                       long afterSeq);

    List<MeetingLiveLine> findByRecordingIdAndPartialFalseOrderBySeqAsc(UUID recordingId);

    List<MeetingLiveLine> findByRecordingIdAndPartialTrue(UUID recordingId);

    @Query("select coalesce(max(l.seq), 0) from MeetingLiveLine l where l.recordingId = :recordingId "
            + "and l.partial = false")
    long lastSeqOf(UUID recordingId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM app_lm_meeting_live_line WHERE recording_id = :recordingId")
    int deleteByRecording(UUID recordingId);
}
