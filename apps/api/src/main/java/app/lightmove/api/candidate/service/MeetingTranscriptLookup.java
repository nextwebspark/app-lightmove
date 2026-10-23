package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.TranscriptOnFile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The transcripts of the calls a position recorded with a person, answered by {@code notetaker}, which already
 * depends on {@code candidate}, so the dependency stays one-way.
 */
public interface MeetingTranscriptLookup {

    /**
     * Newest call first. Only a call whose Meeting note is among {@code liveNoteIds}: a consultant who removed the
     * note took the call out of what the AI reads with it.
     */
    List<TranscriptOnFile> transcriptsOf(UUID workspaceId, UUID projectId, UUID personId,
                                         Collection<UUID> liveNoteIds);
}
