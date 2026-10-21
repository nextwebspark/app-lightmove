package app.lightmove.api.screentext.service;

import app.lightmove.api.screentext.model.ScreenTextCatalogEntry;
import app.lightmove.api.screentext.model.ScreenTextCatalogFile;
import app.lightmove.api.screentext.model.ScreenTextCatalogScreen;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Every sentence the SPA lets a super admin edit, read once from {@code screen-text/catalog.json}. The SPA's suite
 * writes that file from its own catalogue, so a key the code does not know can never be stored or served.
 */
@Component
public class ScreenTextCatalog {

    static final String RESOURCE = "screen-text/catalog.json";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

    private final List<ScreenTextCatalogScreen> screens;
    private final Map<String, ScreenTextCatalogEntry> entries;
    private final List<Pattern> neverOnScreen;
    private final String fingerprint;

    public ScreenTextCatalog(ObjectMapper json) {
        byte[] content = read();
        ScreenTextCatalogFile file = json.readValue(content, ScreenTextCatalogFile.class);
        this.screens = List.copyOf(file.screens());
        this.entries = file.entries().stream().collect(Collectors.toMap(ScreenTextCatalogEntry::key, entry -> entry,
                (first, second) -> {
                    throw new IllegalStateException("Screen text key listed twice: " + first.key());
                }, LinkedHashMap::new));
        this.neverOnScreen = file.neverOnScreen().stream()
                .map(source -> Pattern.compile(source, Pattern.CASE_INSENSITIVE))
                .toList();
        this.fingerprint = sha256(content).substring(0, 12);
    }

    public List<ScreenTextCatalogScreen> screens() {
        return screens;
    }

    public List<ScreenTextCatalogEntry> entries() {
        return List.copyOf(entries.values());
    }

    public Optional<ScreenTextCatalogEntry> entry(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public List<Pattern> neverOnScreen() {
        return neverOnScreen;
    }

    /** Changes with every deploy that changes the catalogue, so a cached bundle is re-filtered against the new one. */
    public String fingerprint() {
        return fingerprint;
    }

    /**
     * The overrides a screen may draw: a key the code still has, holding the placeholders its current default holds.
     * A default reworded in code since the override was written keeps its default rather than losing a value.
     */
    public Map<String, String> servable(Map<String, String> overrides) {
        Map<String, String> servable = new LinkedHashMap<>();
        overrides.forEach((key, text) -> entry(key)
                .filter(entry -> !entry.defaultText().equals(text))
                .filter(entry -> placeholdersOf(text).equals(placeholdersOf(entry.defaultText())))
                .ifPresent(entry -> servable.put(key, text)));
        return servable;
    }

    public static List<String> placeholdersOf(String text) {
        return PLACEHOLDER.matcher(text).results().map(match -> match.group(1)).distinct().sorted().toList();
    }

    private static byte[] read() {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException missing) {
            throw new UncheckedIOException("The SPA's screen text catalogue is missing from the build: " + RESOURCE,
                    missing);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
