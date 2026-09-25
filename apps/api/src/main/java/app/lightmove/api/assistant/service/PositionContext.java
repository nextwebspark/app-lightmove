package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.MandateBrief;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The "position being hired for" block of the system prompt — in the prompt rather than behind a tool
 * because nearly every question depends on it, and a tool cost a whole model round to read it. Like
 * {@link FirmContext}, plain capped lines of data somebody typed, never prose passed through.
 */
final class PositionContext {

    private static final int MAX_TEXT = 600;
    private static final int MAX_ITEM = 200;

    private PositionContext() {
    }

    static String render(MandateBrief brief) {
        List<String> lines = new ArrayList<>();
        line(lines, "Role", brief.roleTitle(), MAX_TEXT);
        line(lines, "Seniority", brief.seniority(), MAX_TEXT);
        line(lines, "Department", brief.department(), MAX_TEXT);
        line(lines, "Location", joined(brief.locationCity(), brief.locationCountry()), MAX_TEXT);
        line(lines, "Why the search exists", brief.mandateReason(), MAX_TEXT);
        line(lines, "Business driver", brief.businessDriver(), MAX_TEXT);
        line(lines, "Strategic priorities", String.join(", ", brief.strategicPriorities()), MAX_TEXT);
        line(lines, "Summary", brief.narrative(), MAX_TEXT);
        brief.responsibilities().forEach(responsibility -> line(lines, "Responsibility", responsibility, MAX_ITEM));
        boolean drafted = !brief.responsibilities().isEmpty() || hasText(brief.narrative())
                || hasText(brief.businessDriver());
        if (!drafted) {
            lines.add("No brief has been written yet.");
        }
        return String.join("\n", lines);
    }

    private static void line(List<String> lines, String label, String value, int max) {
        String clean = flattened(value, max);
        if (clean != null) {
            lines.add("- " + label + ": " + clean);
        }
    }

    private static String joined(String... parts) {
        String joined = Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String flattened(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String flat = value.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
