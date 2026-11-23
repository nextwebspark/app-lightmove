package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.enrichment.candidate.model.GoldenRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a golden set: one {@link GoldenRow} per line, a {@code //} line a comment. Main code opens the committed
 * rows only; a private set's path is opened by the offline suite, which hands its text to {@link #parse}.
 */
public final class GoldenRows {

    private static final String SYNTHETIC = "eval/nationality-synthetic.jsonl";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GoldenRows() {
    }

    /** The committed, invented rows, as written. */
    public static List<JsonNode> synthetic() {
        try (InputStream in = GoldenRows.class.getClassLoader().getResourceAsStream(SYNTHETIC)) {
            if (in == null) {
                throw new IllegalStateException("No golden set at " + SYNTHETIC);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<JsonNode> parse(String jsonl) {
        return jsonl.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                .map(JSON::readTree)
                .toList();
    }

    public static List<GoldenRow> typed(List<JsonNode> rows) {
        return rows.stream().map(row -> JSON.treeToValue(row, GoldenRow.class)).toList();
    }
}
