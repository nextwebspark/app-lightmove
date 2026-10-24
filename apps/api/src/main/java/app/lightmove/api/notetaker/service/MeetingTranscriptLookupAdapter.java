package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.SpokenLine;
import app.lightmove.api.candidate.model.TranscriptOnFile;
import app.lightmove.api.candidate.service.MeetingTranscriptLookup;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers the assessment's transcripts and call records for {@code candidate}: only the position's own calls, read
 * from it. A speaker whose name is a staff member's, or the scheduler's, is marked as the team here, so the model
 * never has to guess from a display name which voice is ours.
 */
@Component
@RequiredArgsConstructor
class MeetingTranscriptLookupAdapter implements MeetingTranscriptLookup {

    private final MeetingRecordingRepository recordings;
    private final TeamVoices teamVoices;

    @Override
    public List<TranscriptOnFile> transcriptsOf(UUID workspaceId, UUID projectId, UUID personId,
                                                Collection<UUID> liveNoteIds) {
        if (liveNoteIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> live = Set.copyOf(liveNoteIds);
        List<MeetingRecording> kept = recordings
                .findByWorkspaceIdAndProjectIdAndPersonIdOrderByStartsAtDescCreatedAtDesc(workspaceId, projectId,
                        personId)
                .stream()
                .filter(recording -> recording.getNoteId() != null && live.contains(recording.getNoteId()))
                .filter(recording -> recording.getTranscript() != null && !recording.getTranscript().isEmpty())
                .toList();
        if (kept.isEmpty()) {
            return List.of();
        }
        Set<String> team = teamNamesOf(workspaceId, kept);
        return kept.stream().map(recording -> transcriptOf(recording, team)).toList();
    }

    private Set<String> teamNamesOf(UUID workspaceId, List<MeetingRecording> recordings) {
        return teamVoices.namesOf(workspaceId, recordings.stream().map(MeetingRecording::getRequestedBy).toList());
    }

    private static TranscriptOnFile transcriptOf(MeetingRecording recording, Set<String> team) {
        return new TranscriptOnFile(recording.getTitle(), recording.getStartsAt(), recording.getTranscript().stream()
                .map(line -> new SpokenLine(line.at(), line.speaker(), line.text(),
                        TeamVoices.isTeam(team, line.speaker(), line.named())))
                .toList(), recording.getCallRecord());
    }
}
