package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiUse;
import app.lightmove.api.candidate.model.SubjectAccessAbout;
import app.lightmove.api.candidate.model.SubjectAccessArchive;
import app.lightmove.api.candidate.model.SubjectAccessPosition;
import app.lightmove.api.candidate.model.SubjectAccessReferences;
import app.lightmove.api.candidate.model.SubjectAccessSection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

/**
 * {@code person.html}: a subject access export read in a browser. One self-contained page — its style inline, no
 * script, nothing fetched — and every value escaped, since a note, an email or a transcript is whatever someone
 * typed or said. A note's Markdown is shown as written, never rendered.
 */
final class SubjectAccessHtml {

    private static final Set<String> SOURCE_COLUMNS = Set.of("source", "enriched_by", "contacts_looked_up_via");
    private static final int INLINE_TEXT_LIMIT = 120;

    private static final String STYLE = """
            :root{color-scheme:light dark;--text:#1d2330;--muted:#5d6577;--line:#d9dde5;--panel:#f6f7f9;--bg:#ffffff}
            @media (prefers-color-scheme:dark){:root{--text:#e7e9ee;--muted:#a3a9b6;--line:#363c48;--panel:#1d212a;\
            --bg:#14171d}}
            body{margin:0;background:var(--bg);color:var(--text);font:14px/1.55 system-ui,-apple-system,"Segoe UI",\
            sans-serif}
            main{max-width:880px;margin:0 auto;padding:32px 16px 64px}
            h1{font-size:24px;margin:0 0 4px}h2{font-size:18px;margin:36px 0 10px;padding-bottom:6px;\
            border-bottom:1px solid var(--line)}h3{font-size:14px;margin:18px 0 4px}
            .meta,.empty{color:var(--muted)}
            table{width:100%;border-collapse:collapse;margin:0 0 12px;background:var(--panel);border:1px solid \
            var(--line);border-radius:8px;table-layout:fixed}
            th,td{padding:6px 10px;text-align:left;vertical-align:top;border-top:1px solid var(--line);\
            overflow-wrap:anywhere}
            tr:first-child th,tr:first-child td{border-top:0}
            th{width:32%;font-weight:600;color:var(--muted)}
            pre{margin:0;white-space:pre-wrap;font:12.5px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}
            ul{padding-left:20px}
            """;

    private SubjectAccessHtml() {}

    static String render(SubjectAccessArchive archive) {
        StringBuilder page = new StringBuilder(64 * 1024);
        page.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Personal data held on ").append(escape(archive.personName())).append("</title>")
                .append("<style>").append(STYLE).append("</style></head><body><main>")
                .append("<h1>Personal data held on ").append(escape(archive.personName())).append("</h1>")
                .append("<p class=\"meta\">Held by ").append(escape(archive.workspaceName()))
                .append(". Exported by ").append(escape(archive.exportedBy())).append(" on ")
                .append(escape(archive.exportedAt().toString().substring(0, 10))).append(".</p>");
        about(page, archive.about());
        aiLogic(page, archive);
        for (SubjectAccessSection section : archive.sections()) {
            section(page, section, archive);
        }
        names(page, archive.references());
        return page.append("</main></body></html>").toString();
    }

    private static void about(StringBuilder page, SubjectAccessAbout about) {
        page.append("<h2>About this data</h2><table>");
        row(page, "Who holds it", escape(about.controller()));
        row(page, "Why", escape(about.purposes()));
        StringBuilder recipients = new StringBuilder("<ul>");
        about.recipients().forEach(recipient -> recipients.append("<li>").append(escape(recipient)).append("</li>"));
        row(page, "Who reads it", recipients.append("</ul>").toString());
        row(page, "How long it is kept", escape(about.retention()));
        StringBuilder sources = new StringBuilder("<ul>");
        about.sources().forEach((code, meaning) -> sources.append("<li><b>").append(escape(code)).append("</b>: ")
                .append(escape(meaning)).append("</li>"));
        row(page, "Where it came from", "Each record and contact names its source:" + sources.append("</ul>"));
        row(page, "Your rights", escape(about.rights()));
        row(page, "Deleted notes", escape(about.deletedNotes()));
        row(page, "Other people", escape(about.otherPeople()));
        row(page, "Automated decisions", escape(about.automatedDecisions()));
        page.append("</table>");
    }

    private static void aiLogic(StringBuilder page, SubjectAccessArchive archive) {
        page.append("<h2>How the AI works on your record</h2>");
        if (archive.aiUses().isEmpty()) {
            page.append("<p class=\"empty\">No language model has left anything on your record.</p>");
            return;
        }
        for (AiUse use : archive.aiUses()) {
            page.append("<h3>").append(escape(use.title())).append("</h3><p>").append(escape(use.logic()))
                    .append("</p>");
        }
    }

    private static void section(StringBuilder page, SubjectAccessSection section, SubjectAccessArchive archive) {
        page.append("<h2>").append(escape(section.title())).append("</h2>");
        if (section.rows().isEmpty()) {
            page.append("<p class=\"empty\">Nothing held.</p>");
            return;
        }
        for (Map<String, Object> values : section.rows()) {
            page.append("<table>");
            values.forEach((column, value) -> {
                if (value != null && !column.equals("person_id")
                        && !(value instanceof JsonNode node && (node.isNull() || node.isEmpty()))) {
                    row(page, labelOf(column, isNamed(value, archive.references())),
                            valueOf(column, value, archive));
                }
            });
            page.append("</table>");
        }
    }

    private static void names(StringBuilder page, SubjectAccessReferences references) {
        page.append("<h2>Names in this file</h2>");
        if (!references.positions().isEmpty()) {
            page.append("<h3>Positions</h3><table>");
            references.positions().forEach((id, position) -> row(page, escape(position.title()),
                    escape(positionLine(position) + " · " + id)));
            page.append("</table>");
        }
        if (!references.colleagues().isEmpty()) {
            page.append("<h3>Colleagues</h3><table>");
            references.colleagues().forEach((id, name) -> row(page, escape(name), escape(id)));
            page.append("</table>");
        }
    }

    private static String positionLine(SubjectAccessPosition position) {
        String line = position.hiringFor() == null ? "Position" : "For " + position.hiringFor();
        return position.archivedAt() == null ? line : line + ", archived " + position.archivedAt().substring(0, 10);
    }

    private static String valueOf(String column, Object value, SubjectAccessArchive archive) {
        if (value instanceof JsonNode node) {
            return node.isString() ? text(node.asString()) : "<pre>" + escape(node.toPrettyString()) + "</pre>";
        }
        String plain = String.valueOf(value);
        SubjectAccessReferences references = archive.references();
        if (references.colleagues().containsKey(plain)) {
            return escape(references.colleagues().get(plain));
        }
        if (references.positions().containsKey(plain)) {
            return escape(references.positions().get(plain).title());
        }
        if (references.statuses().containsKey(plain)) {
            return escape(references.statuses().get(plain));
        }
        String meaning = SOURCE_COLUMNS.contains(column)
                ? archive.about().sources().get(plain.toLowerCase(Locale.ROOT)) : null;
        return meaning == null ? text(plain) : escape(plain) + " — " + escape(meaning);
    }

    private static String text(String value) {
        return value.length() > INLINE_TEXT_LIMIT || value.contains("\n")
                ? "<pre>" + escape(value) + "</pre>" : escape(value);
    }

    private static void row(StringBuilder page, String label, String html) {
        page.append("<tr><th>").append(label).append("</th><td>").append(html).append("</td></tr>");
    }

    private static boolean isNamed(Object value, SubjectAccessReferences references) {
        String plain = String.valueOf(value);
        return references.colleagues().containsKey(plain) || references.positions().containsKey(plain)
                || references.statuses().containsKey(plain);
    }

    /**
     * {@code ai_nationality_reading} reads as "AI nationality reading"; an id shown by what it names drops its "ID",
     * so {@code project_id} reads as "Position" and {@code author_user_id} as "Author".
     */
    static String labelOf(String column, boolean named) {
        String readable = named ? column.replaceFirst("(_user)?_id$", "") : column;
        String[] words = (readable.equals("project") ? "position" : readable).split("_");
        StringBuilder label = new StringBuilder();
        for (int index = 0; index < words.length; index++) {
            String word = switch (words[index]) {
                case "ai" -> "AI";
                case "id" -> "ID";
                case "url" -> "URL";
                case "linkedin" -> "LinkedIn";
                default -> index == 0 && !words[index].isEmpty()
                        ? Character.toUpperCase(words[index].charAt(0)) + words[index].substring(1) : words[index];
            };
            label.append(index == 0 ? "" : " ").append(word);
        }
        return escape(label.toString());
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
