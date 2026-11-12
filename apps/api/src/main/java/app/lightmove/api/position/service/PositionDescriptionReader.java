package app.lightmove.api.position.service;

import app.lightmove.api.position.repository.PositionDocumentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The text of the position description the client gave, for the AI assessment to benchmark an executive against —
 * redacted the way position extraction redacts it, so the client is named to the model as a placeholder.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionDescriptionReader {

    private final PositionBriefLoader briefs;
    private final PositionDocumentRepository documents;
    private final PositionDocumentTextReader textReader;
    private final PositionDocumentRedactor redactor;

    /** Empty with no description attached, or one that will not read: the brief's own fields still count. */
    @Transactional(readOnly = true)
    public Optional<String> textOf(UUID workspaceId, UUID projectId, int maxCharacters) {
        PositionBrief brief = briefs.read(workspaceId, projectId);
        if (brief.position().getId() == null) {
            return Optional.empty();
        }
        UUID clientId = brief.project().getClientId();
        return documents.findByPositionId(brief.position().getId())
                .flatMap(document -> readable(document.getContent(), maxCharacters))
                .map(text -> redactor.redact(text, clientId, workspaceId).text())
                .filter(text -> !text.isBlank());
    }

    private Optional<String> readable(byte[] content, int maxCharacters) {
        try {
            return Optional.of(textReader.readUpTo(content, maxCharacters));
        } catch (RuntimeException unreadable) {
            log.info("A position description was left out of an AI assessment: {}", unreadable.toString());
            return Optional.empty();
        }
    }
}
