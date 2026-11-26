package app.lightmove.api.candidate.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A client read filters on {@code shown_to_client} (V246, #1488), which also hides someone restricted or refusing to be
 * shared; staff's own {@code client_visible} switch alone would let them back in. Scans every string in the
 * application's sources, so JPQL, native SQL and {@code JdbcTemplate} alike, and every derived query method's name.
 */
class ClientReadsAskShownToClientTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Pattern THE_SWITCH = Pattern.compile("\\b(client_visible|clientVisible)\\b");
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern DERIVED_QUERY_ON_THE_SWITCH =
            Pattern.compile("\\b(find|count|exists|delete|stream)\\w*ClientVisible\\w*\\s*\\(");

    /** The entity's own column mapping, which staff's switch writes through; nothing reads a client's view there. */
    private static final Set<Path> STAFF_WRITER = Set.of(
            Path.of("app", "lightmove", "api", "candidate", "model", "Candidate.java"));

    @Test
    @DisplayName("no string or derived query in the application filters a client's read on staff's switch alone")
    void noQueryReadsTheSwitchAlone() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !STAFF_WRITER.contains(MAIN_SOURCES.relativize(path)))
                    .forEach(path -> offenders.addAll(offendersIn(path)));
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("the scan reaches the entity it allows, so a moved source tree cannot pass it by finding nothing")
    void theScanFindsTheStaffWriter() {
        Path writer = MAIN_SOURCES.resolve(STAFF_WRITER.iterator().next());

        assertThat(offendersIn(writer)).isNotEmpty();
    }

    private static List<String> offendersIn(Path source) {
        String code = read(source);
        List<String> found = new ArrayList<>();
        Matcher blocks = TEXT_BLOCK.matcher(code);
        while (blocks.find()) {
            if (THE_SWITCH.matcher(blocks.group(1)).find()) {
                found.add(source + ": a text block names the switch");
            }
        }
        Matcher literals = STRING_LITERAL.matcher(TEXT_BLOCK.matcher(code).replaceAll("\"\""));
        while (literals.find()) {
            if (THE_SWITCH.matcher(literals.group(1)).find()) {
                found.add(source + ": " + literals.group());
            }
        }
        Matcher derived = DERIVED_QUERY_ON_THE_SWITCH.matcher(code);
        while (isRepository(source) && derived.find()) {
            found.add(source + ": " + derived.group());
        }
        return found;
    }

    private static boolean isRepository(Path source) {
        return source.getParent().getFileName().toString().equals("repository");
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
