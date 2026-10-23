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

/** Answers the assessment's transcripts for {@code candidate}: only the position's own calls, read from it. */
@Component
@RequiredArgsConstructor
class MeetingTranscriptLookupAdapter implements MeetingTranscriptLookup {

    private final MeetingRecordingRepository recordings;

    @Override
    public List<TranscriptOnFile> transcriptsOf(UUID workspaceId, UUID projectId, UUID personId,
                                                Collection<UUID> liveNoteIds) {
        if (liveNoteIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> live = Set.copyOf(liveNoteIds);
        return recordings.findByWorkspaceIdAndProjectIdAndPersonIdOrderByStartsAtDescCreatedAtDesc(
                        workspaceId, projectId, personId)
                .stream()
                .filter(recording -> recording.getNoteId() != null && live.contains(recording.getNoteId()))
                .filter(recording -> recording.getTranscript() != null && !recording.getTranscript().isEmpty())
                .map(MeetingTranscriptLookupAdapter::transcriptOf)
                .toList();
    }

    private static TranscriptOnFile transcriptOf(MeetingRecording recording) {
        return new TranscriptOnFile(recording.getTitle(), recording.getStartsAt(), recording.getTranscript().stream()
                .map(line -> new SpokenLine(line.at(), line.speaker(), line.text()))
                .toList());
    }
}
