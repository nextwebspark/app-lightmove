package app.lightmove.api.core.platformconfig.service;

import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The prompts as shipped in the jar, read once: a catalogued prompt at construction, so a missing file fails the
 * boot, and any other id (an eval's frozen {@code -baseline}) on first ask.
 */
@Component
public class ShippedPrompts {

    /** {@code StTemplateRenderer}'s delimiters, as the assistant's system text is rendered with. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final Map<PlatformPrompt, String> catalogued = new EnumMap<>(PlatformPrompt.class);
    private final Map<String, String> uncatalogued = new ConcurrentHashMap<>();

    public ShippedPrompts() {
        for (PlatformPrompt prompt : PlatformPrompt.values()) {
            catalogued.put(prompt, read(prompt.resource()));
        }
    }

    public String of(PlatformPrompt prompt) {
        return catalogued.get(prompt);
    }

    /** A structured prompt's system text by id, whether or not the catalog lists it. */
    public String systemTextOf(String promptId) {
        return PlatformPrompt.byId(promptId).map(this::of)
                .orElseGet(() -> uncatalogued.computeIfAbsent(promptId,
                        id -> read("prompts/" + id + "-system.st")));
    }

    /** The template variables {@code text} names, which an override must keep exactly. */
    public static Set<String> placeholdersIn(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        return matcher.results().map(match -> match.group(1)).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String read(String resource) {
        try {
            return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Could not read the shipped prompt " + resource, unreadable);
        }
    }
}
