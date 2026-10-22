package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.HeardLine;
import app.lightmove.api.notetaker.model.TranscribedPiece;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** A run's transcript (V153): what each audio piece was heard to say as the call goes, then the whole of it. */
@Component
@RequiredArgsConstructor
class MeetingTranscriptPieces {

    private static final TypeReference<List<HeardLine>> HEARD_LINES = new TypeReference<>() {};
    private static final TypeReference<List<StoredLine>> STORED_LINES = new TypeReference<>() {};
    /** How a transcript kept before lines said whether they were named tells an unmatched voice: its label. */
    private static final Pattern UNNAMED_VOICE = Pattern.compile("Speaker( \\d+)?");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    /** Transcribing a piece twice — two passes racing — keeps the first answer. */
    void store(UUID recordingId, TranscribedPiece piece) {
        insert(recordingId, piece, false);
    }

    /** A piece no pass could hear: a gap in the transcript, which the note owns up to. */
    void storeSkipped(UUID recordingId, int sequence, Instant startedAt) {
        insert(recordingId, new TranscribedPiece(sequence, startedAt, List.of()), true);
    }

    int skippedCountOf(UUID recordingId) {
        Integer skipped = jdbc.queryForObject("SELECT count(*) FROM app_lm_meeting_transcript_piece "
                + "WHERE recording_id = ? AND skipped", Integer.class, recordingId);
        return skipped == null ? 0 : skipped;
    }

    private void insert(UUID recordingId, TranscribedPiece piece, boolean skipped) {
        jdbc.update("""
                INSERT INTO app_lm_meeting_transcript_piece (recording_id, sequence, started_at, lines, skipped)
                VALUES (?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (recording_id, sequence) DO NOTHING""",
                recordingId, piece.sequence(), Timestamp.from(piece.startedAt()), json.writeValueAsString(piece.lines()),
                skipped);
    }

    List<TranscribedPiece> piecesOf(UUID recordingId) {
        return jdbc.query("""
                SELECT sequence, started_at, lines::text AS lines FROM app_lm_meeting_transcript_piece
                WHERE recording_id = ? ORDER BY started_at, sequence""",
                (row, index) -> new TranscribedPiece(row.getInt("sequence"),
                        row.getTimestamp("started_at").toInstant(),
                        json.readValue(row.getString("lines"), HEARD_LINES)),
                recordingId);
    }

    void forgetPieces(UUID recordingId) {
        jdbc.update("DELETE FROM app_lm_meeting_transcript_piece WHERE recording_id = ?", recordingId);
    }

    /**
     * Remove notetaker keeps nothing, so what was transcribed before a removal goes with the audio; a failed run's
     * goes once it has been kept {@code failedBefore} for Try again, as its pieces do.
     */
    void forgetUnwanted(Instant failedBefore) {
        jdbc.update("""
                DELETE FROM app_lm_meeting_transcript_piece piece
                USING app_lm_meeting_recording recording
                WHERE recording.id = piece.recording_id
                  AND (recording.status = 'REMOVED'
                       OR (recording.status = 'FAILED' AND recording.updated_at < ?))""",
                Timestamp.from(failedBefore));
    }

    void keepTranscript(UUID recordingId, List<TranscriptLine> transcript) {
        List<StoredLine> stored = transcript.stream()
                .map(line -> new StoredLine(line.at().toString(), line.speaker(), line.text(), line.named()))
                .toList();
        jdbc.update("UPDATE app_lm_meeting_recording SET transcript = ?::jsonb WHERE id = ?",
                json.writeValueAsString(stored), recordingId);
    }

    Optional<List<TranscriptLine>> transcriptOf(UUID recordingId) {
        return jdbc.query("SELECT transcript::text AS transcript FROM app_lm_meeting_recording WHERE id = ?",
                        (row, index) -> row.getString("transcript"), recordingId).stream()
                .filter(text -> text != null)
                .findFirst()
                .map(text -> json.readValue(text, STORED_LINES).stream()
                        .map(line -> new TranscriptLine(Instant.parse(line.at()), line.speaker(), line.text(),
                                line.named() != null ? line.named()
                                        : !UNNAMED_VOICE.matcher(line.speaker()).matches()))
                        .toList());
    }

    /** The stored shape: an instant as its ISO text, so the column reads the same whoever wrote it. */
    private record StoredLine(String at, String speaker, String text, Boolean named) {}
}
