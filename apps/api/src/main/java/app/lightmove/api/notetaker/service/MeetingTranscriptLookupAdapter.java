package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.SpokenLine;
import app.lightmove.api.candidate.model.TranscriptOnFile;
import app.lightmove.api.candidate.service.MeetingTranscriptLookup;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers the assessment's transcripts for {@code candidate}: only the position's own calls, read from it. A speaker
 * whose name is a staff member's, or the scheduler's, is marked as the team here, so the model never has to guess
 * from a display name which voice is ours.
 */
@Component
@RequiredArgsConstructor
class MeetingTranscriptLookupAdapter implements MeetingTranscriptLookup {

    private final MeetingRecordingRepository recordings;
    private final WorkspaceAccess workspaceAccess;
    private final UserRepository users;

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
        List<UUID> userIds = Stream.concat(
                        workspaceAccess.activeStaff(workspaceId).stream().map(WorkspaceMember::getUserId),
                        recordings.stream().map(MeetingRecording::getRequestedBy))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return users.findAllById(userIds).stream()
                .map(User::getFullName)
                .map(MeetingTranscriptLookupAdapter::nameKey)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }

    private static TranscriptOnFile transcriptOf(MeetingRecording recording, Set<String> team) {
        return new TranscriptOnFile(recording.getTitle(), recording.getStartsAt(), recording.getTranscript().stream()
                .map(line -> new SpokenLine(line.at(), line.speaker(), line.text(),
                        line.named() && team.contains(nameKey(line.speaker()))))
                .toList());
    }

    private static String nameKey(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
