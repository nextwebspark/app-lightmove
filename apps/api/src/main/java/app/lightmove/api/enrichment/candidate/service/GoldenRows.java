package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.enrichment.candidate.model.GoldenRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** Reads a golden set: one {@link GoldenRow} per line, a {@code //} line a comment. */
public final class GoldenRows {

    /** The committed, invented rows. A real set is a private path and never enters the repository. */
    public static final String SYNTHETIC = "classpath:eval/nationality-synthetic.jsonl";

    private static final String CLASSPATH = "classpath:";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GoldenRows() {
    }

    public static List<GoldenRow> read(String location) {
        try (InputStream in = location.startsWith(CLASSPATH)
                ? GoldenRows.class.getClassLoader().getResourceAsStream(location.substring(CLASSPATH.length()))
                : Files.newInputStream(Path.of(location))) {
            if (in == null) {
                throw new IllegalStateException("No golden set at " + location);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                    .map(line -> JSON.readValue(line, GoldenRow.class))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
