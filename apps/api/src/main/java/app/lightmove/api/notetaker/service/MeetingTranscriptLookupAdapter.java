package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.constant.CallSpeakerRole;
import app.lightmove.api.candidate.model.SpokenLine;
import app.lightmove.api.candidate.model.TranscriptOnFile;
import app.lightmove.api.candidate.service.MeetingTranscriptLookup;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.SpeakerVoice;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers the assessment's transcripts for {@code candidate}: only the position's own calls, read from it. Every voice
 * is given its role here — the team's, the executive's, someone else's — by the rules the live copilot uses, so the
 * model never has to guess from a display name whose words are the executive's evidence.
 */
@Component
@RequiredArgsConstructor
class MeetingTranscriptLookupAdapter implements MeetingTranscriptLookup {

    private final MeetingRecordingRepository recordings;
    private final TeamVoices teamVoices;

    @Override
    public List<TranscriptOnFile> transcriptsOf(UUID workspaceId, UUID projectId, UUID personId,
                                                String executiveName, Collection<UUID> liveNoteIds) {
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
        return kept.stream().map(recording -> transcriptOf(recording, team, executiveName)).toList();
    }

    private Set<String> teamNamesOf(UUID workspaceId, List<MeetingRecording> recordings) {
        return teamVoices.namesOf(workspaceId, recordings.stream().map(MeetingRecording::getRequestedBy).toList());
    }

    private static TranscriptOnFile transcriptOf(MeetingRecording recording, Set<String> team, String executiveName) {
        List<TranscriptLine> lines = recording.getTranscript();
        Set<String> executiveVoices = TeamVoices.executiveVoicesOf(executiveName,
                lines.stream().map(line -> new SpeakerVoice(line.speaker(), line.named())).toList(), team);
        return new TranscriptOnFile(recording.getTitle(), recording.getStartsAt(), lines.stream()
                .map(line -> new SpokenLine(line.at(), line.speaker(), line.text(),
                        roleOf(line, team, executiveVoices)))
                .toList());
    }

    static CallSpeakerRole roleOf(TranscriptLine line, Set<String> team, Set<String> executiveVoices) {
        if (TeamVoices.isTeam(team, line.speaker(), line.named())) {
            return CallSpeakerRole.TEAM;
        }
        if (executiveVoices.isEmpty()) {
            return CallSpeakerRole.UNCONFIRMED;
        }
        return line.speaker() != null && executiveVoices.contains(line.speaker())
                ? CallSpeakerRole.CANDIDATE : CallSpeakerRole.OTHER;
    }
}
