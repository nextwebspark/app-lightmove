package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.notetaker.model.CaptionedLine;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A run's captions as the bot uploaded them: each piece holds the lines that changed since the last, so a line
 * arrives again as it grows and its latest reading is the one kept. A piece that will not read is passed over.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class CaptionPieces {

    private final MeetingMediaPieces pieces;
    private final DocumentStore store;
    private final JsonMapper json;

    /** {@code transactions} is the caller's: the key list is read in its own short transaction. */
    List<CaptionedLine> of(UUID recordingId, TransactionOperations transactions) {
        Map<Long, CaptionedLine> byId = new LinkedHashMap<>();
        for (String key : transactions.execute(status -> pieces.captionKeysOf(recordingId))) {
            try (InputStream content = store.open(key)) {
                for (JsonNode line : json.readTree(content.readAllBytes()).path("lines")) {
                    CaptionedLine caption = new CaptionedLine(line.path("id").asLong(),
                            line.path("speaker").asString(""), line.path("text").asString(""),
                            Instant.parse(line.path("startedAt").asString()),
                            Instant.parse(line.path("updatedAt").asString()));
                    byId.merge(caption.id(), caption, (kept, later) ->
                            later.updatedAt().isBefore(kept.updatedAt()) ? kept : later);
                }
            } catch (IOException | RuntimeException unreadable) {
                log.warn("Notetaker run {} captions {} unreadable: {}", recordingId, key, unreadable.toString());
            }
        }
        List<CaptionedLine> captions = new ArrayList<>(byId.values());
        captions.sort(Comparator.comparing(CaptionedLine::startedAt));
        return captions;
    }
}
