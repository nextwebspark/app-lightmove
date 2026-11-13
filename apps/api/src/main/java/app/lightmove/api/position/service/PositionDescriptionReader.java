package app.lightmove.api.position.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The text of the position description the client gave, for the AI assessment to benchmark an executive against —
 * redacted the way position extraction redacts it, so the client is named to the model as a placeholder. The file is
 * read in the loader's short transaction and parsed after it, so no connection is held across the parse.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionDescriptionReader {

    private final ExtractionDocumentLoader loader;
    private final PositionDocumentTextReader textReader;
    private final PositionDocumentRedactor redactor;

    /** Empty with no description attached, or one that will not read: the brief's own fields still count. */
    public Optional<String> textOf(UUID workspaceId, UUID projectId, int maxCharacters) {
        return loader.find(workspaceId, projectId)
                .flatMap(document -> readable(document.content(), maxCharacters)
                        .map(text -> redactor.redact(text, document.clientId(), workspaceId).text()))
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
