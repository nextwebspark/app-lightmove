package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.LastCallResponse;
import app.lightmove.api.candidate.model.LastCallRow;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Last call: the newest live call note on a person, read across every position like Recently approached. */
@Service
@RequiredArgsConstructor
@NamedInterface(name = "api", propagate = false)
public class LastCallReader {

    private final PersonNoteRepository notes;

    @Transactional(readOnly = true)
    public Optional<LastCallResponse> lastCallOf(UUID workspaceId, UUID personId) {
        return notes.findNewestCalls(workspaceId, personId, Limit.of(1)).stream()
                .findFirst()
                .map(LastCallReader::responseOf);
    }

    private static LastCallResponse responseOf(LastCallRow call) {
        return new LastCallResponse(call.getAt(), call.getOutcome() == null ? null : call.getOutcome().value(),
                call.getByUserId(), call.getByName());
    }
}
