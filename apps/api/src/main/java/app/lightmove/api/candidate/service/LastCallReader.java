package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.dto.LastCallResponse;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Last call: the newest live call note on a person, read across every position like Recently approached. */
@Service
@RequiredArgsConstructor
public class LastCallReader {

    private final PersonNoteRepository notes;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Optional<LastCallResponse> lastCallOf(UUID workspaceId, UUID personId) {
        return notes.findNewestOfKind(workspaceId, personId, PersonNoteKind.CALL, Limit.of(1)).stream()
                .findFirst()
                .map(this::responseOf);
    }

    private LastCallResponse responseOf(PersonNote call) {
        return new LastCallResponse(call.happenedAt(),
                call.getOutcome() == null ? null : call.getOutcome().value(),
                call.getAuthorUserId(),
                users.findById(call.getAuthorUserId()).map(User::getFullName).orElse(null));
    }
}
