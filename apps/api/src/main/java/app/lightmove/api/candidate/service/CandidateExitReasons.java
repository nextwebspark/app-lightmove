package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.CandidateExitReasonResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.ExitReason;
import app.lightmove.api.candidate.repository.ExitReasonRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which reason a move out of process is given, and how staff read it back. A way out that offers reasons asks for one;
 * one that offers none, and every status in process, takes none.
 */
@Component
@RequiredArgsConstructor
class CandidateExitReasons {

    private final ExitReasonRepository reasons;
    private final UserRepository users;

    /** The reason a move to {@code status} names, refused unless the status offers it; null where it offers none. */
    ExitReason requireForMove(UUID workspaceId, CandidateStatusDefinition status, UUID reasonId, String note) {
        List<ExitReason> offered = status.isOutOfProcess() ? reasons.findOfferedAt(workspaceId, status.getId())
                : List.of();
        if (offered.isEmpty()) {
            if (reasonId != null) {
                throw ApiException.of(ErrorCode.EXIT_REASON_NOT_OFFERED);
            }
            return null;
        }
        if (reasonId == null) {
            throw ApiException.of(ErrorCode.EXIT_REASON_REQUIRED);
        }
        ExitReason reason = offered.stream().filter(candidate -> candidate.getId().equals(reasonId)).findFirst()
                .orElseThrow(() -> refusalFor(workspaceId, reasonId));
        return requireNote(reason, note);
    }

    /**
     * A new reason for someone already out of process. The one they hold may be kept even once retired or unlinked,
     * so a note can still be corrected; any other must be one their status offers now.
     */
    ExitReason requireForChange(UUID workspaceId, Candidate candidate, UUID reasonId, String note) {
        CandidateStatusDefinition status = candidate.getStatus();
        if (!status.isOutOfProcess()) {
            throw ApiException.of(ErrorCode.EXIT_REASON_NOT_OFFERED);
        }
        if (reasonId.equals(candidate.getExitReasonId())) {
            return requireNote(reasons.requireInWorkspace(reasonId, workspaceId), note);
        }
        return reasons.findOfferedAt(workspaceId, status.getId()).stream()
                .filter(offered -> offered.getId().equals(reasonId)).findFirst()
                .map(reason -> requireNote(reason, note))
                .orElseThrow(() -> refusalFor(workspaceId, reasonId));
    }

    /** Each candidate's reason, for those holding one. */
    List<CandidateExitReasonResponse> givenTo(UUID workspaceId, Collection<Candidate> candidates) {
        List<Candidate> given = candidates.stream().filter(row -> row.getExitReasonId() != null).toList();
        if (given.isEmpty()) {
            return List.of();
        }
        Map<UUID, ExitReason> byId = reasons.findByWorkspaceIdAndIdIn(workspaceId,
                given.stream().map(Candidate::getExitReasonId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ExitReason::getId, Function.identity()));
        Map<UUID, User> setters = users.findAllById(given.stream().map(Candidate::getExitReasonSetBy)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return given.stream()
                .filter(row -> byId.containsKey(row.getExitReasonId()))
                .map(row -> {
                    ExitReason reason = byId.get(row.getExitReasonId());
                    User setter = row.getExitReasonSetBy() == null ? null : setters.get(row.getExitReasonSetBy());
                    return new CandidateExitReasonResponse(row.getId(), reason.getId(), reason.getLabel(),
                            row.getExitNote(), reason.isRetired(), row.getExitReasonSetAt(), row.getExitReasonSetBy(),
                            setter == null ? null : setter.getFullName());
                })
                .toList();
    }

    CandidateExitReasonResponse givenTo(UUID workspaceId, Candidate candidate) {
        return givenTo(workspaceId, List.of(candidate)).stream().findFirst().orElse(null);
    }

    private ApiException refusalFor(UUID workspaceId, UUID reasonId) {
        return reasons.findByIdAndWorkspaceId(reasonId, workspaceId).filter(ExitReason::isRetired).isPresent()
                ? ApiException.of(ErrorCode.EXIT_REASON_RETIRED)
                : ApiException.of(ErrorCode.EXIT_REASON_NOT_OFFERED);
    }

    private static ExitReason requireNote(ExitReason reason, String note) {
        if (reason.isRequiresNote() && (note == null || note.isBlank())) {
            throw ApiException.of(ErrorCode.EXIT_REASON_NOTE_REQUIRED);
        }
        return reason;
    }
}
