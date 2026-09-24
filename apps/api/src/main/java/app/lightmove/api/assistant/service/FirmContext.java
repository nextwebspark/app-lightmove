package app.lightmove.api.assistant.service;

import app.lightmove.api.workspace.model.FirmFacts;
import app.lightmove.api.workspace.model.WorkspacePersona;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The "about the firm" block of the system prompt. Short on purpose — it rides on every model call —
 * and plain lines only: the persona is text a workspace admin wrote, so it is framed as data about
 * the firm and cut to length, never passed through as prose the model might read as instructions.
 */
final class FirmContext {

    private static final int MAX_ITEMS = 10;
    private static final int MAX_TEXT = 600;

    private FirmContext() {
    }

    static String render(FirmFacts firm) {
        List<String> lines = new ArrayList<>();
        line(lines, "Name", firm.name());
        line(lines, "Industry", firm.industry());
        line(lines, "Headquarters", joined(", ", firm.city(), firm.country()));
        line(lines, "Headcount", firm.employees() == null ? null
                : String.format(Locale.ROOT, "%,d", firm.employees()));
        line(lines, "Website", firm.website());
        WorkspacePersona persona = firm.persona();
        if (persona != null) {
            line(lines, "What it does", persona.summary());
            line(lines, "Sectors", listed(persona.sectors()));
            line(lines, "Competitors", listed(persona.competitors()));
            line(lines, "Geographies", listed(persona.geographies()));
            line(lines, "Notes", persona.notes());
        }
        boolean onlyName = lines.size() <= 1;
        return onlyName
                ? String.join("\n", lines) + (lines.isEmpty() ? "" : "\n") + "Nothing else is recorded about the firm yet."
                : String.join("\n", lines);
    }

    private static void line(List<String> lines, String label, String value) {
        String clean = flattened(value);
        if (clean != null) {
            lines.add("- " + label + ": " + clean);
        }
    }

    private static String listed(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().limit(MAX_ITEMS).collect(Collectors.joining(", "));
    }

    private static String joined(String separator, String... parts) {
        String joined = Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    private static String flattened(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String flat = value.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAX_TEXT ? flat : flat.substring(0, MAX_TEXT) + "…";
    }
}
