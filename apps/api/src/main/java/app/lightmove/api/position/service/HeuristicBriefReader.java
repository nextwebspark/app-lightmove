package app.lightmove.api.position.service;

import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ProposedPositionDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reads step one's fields out of a position description's own conventional structure, with no model
 * call at all. Mirrors {@code dataimport}'s {@code HeuristicColumnMatcher} in spirit rather than
 * shape — a column-header lookup and a reading task have different jobs — but the same reason for
 * existing: Vertex AI needs Application Default Credentials on every path including a plain local
 * run, and a feature that needed them to demo is one most of the team never sees.
 *
 * <p><b>Not a seed for the model.</b> {@code HeuristicColumnMatcher} seeds the mapping prompt, because
 * that is a lookup task; extraction is a <i>reading</i> task, and handing the model a first draft
 * anchors it — it would confirm a wrong heuristic title rather than read the document itself. This
 * reader's answer never reaches the prompt. It is the ultimate fallback when the model cannot be
 * reached or is blocked, and it feeds exactly one cross-check: where the model and this reader agree
 * on the role title, {@link PositionDetailsProposer} upgrades that field's confidence.
 *
 * <p>Four rules, run independently: a key-value header block, a bulleted responsibilities section, an
 * employment-type keyword search, and a seniority reading that reuses {@link PositionTemplateService}
 * rather than duplicating its curated title-to-seniority mapping.
 */
@Service
@RequiredArgsConstructor
public class HeuristicBriefReader {

    /**
     * A header line's label and value, separated by a colon <b>or a run of two-or-more spaces or a
     * tab</b> — the second form is not optional. It is how a PDF's key-value table survives text
     * extraction: {@code JD_CEO.pdf}'s "Job Title    CEO" carries no colon at all, only the gap a
     * table cell leaves.
     */
    private static final Pattern HEADER_LINE = Pattern.compile(
            "(?im)^\\s*(Job Title|Position|Role|Title|Company|Department|Division|Function|Location|"
                    + "Based in|Reports to|Reporting to|Employment Type|Contract Type|Grade)"
                    + "\\s*(?::\\s*|[ \\t]{2,})(.+?)\\s*$");

    /** Where a header's captured value spills into the next column's label across a wide gap. */
    private static final Pattern COLUMN_GAP = Pattern.compile("[ \\t]{2,}");

    /**
     * A heading-shaped or lead-in sentence naming the responsibilities section. A whole-line match
     * would miss real documents, which phrase it as a sentence ("Specifically, responsibilities
     * include the following:") rather than a bare heading — so this only requires the keyword to
     * appear, guarded by a length ceiling so an ordinary paragraph mentioning "the position" in
     * passing is not mistaken for one.
     */
    private static final Pattern RESPONSIBILITIES_KEYWORD = Pattern.compile(
            "(?i)\\b(key focus areas|core responsibilities|responsibilities|accountabilities|duties|"
                    + "the position)\\b");
    private static final int HEADING_LINE_MAX_LENGTH = 150;

    /** A short, capitalised, markup-free line — the shape a section heading takes once extracted. */
    private static final Pattern SECTION_HEADING = Pattern.compile("^[A-Z][A-Za-z /&]{1,45}:?$");

    /**
     * A bullet marker at the start of a line: a dash, any short run of symbol characters (a PDF's
     * bullet glyph rarely survives extraction as the exact Unicode bullet it was drawn as — {@code
     * JD_CEO.pdf}'s bullet extracts as a middle dot, another font's as something else entirely), or a
     * number or letter followed by {@code .}/{@code )}.
     */
    private static final Pattern BULLET_LINE = Pattern.compile(
            "^\\s*(?:[^\\w\\s]{1,2}|\\d+[.)]|[a-zA-Z][.)])\\s+(.+?)\\s*$");

    private static final String FALLBACK_TEMPLATE_CODE = "generic-executive";

    private final PositionTemplateService templates;

    public ProposedPositionDetails propose(UUID workspaceId, String documentText) {
        List<ExtractedField> fields = new ArrayList<>();

        HeaderFields header = readHeaderBlock(documentText);
        header.roleTitle().ifPresent(fields::add);
        header.department().ifPresent(fields::add);
        header.location().ifPresent(fields::add);
        fields.addAll(readResponsibilities(documentText));
        readEmploymentType(header.employmentTypeHint().orElse(documentText)).ifPresent(fields::add);
        header.roleTitle().ifPresent(title ->
                readSeniority(workspaceId, title.value()).ifPresent(fields::add));

        return new ProposedPositionDetails(ExtractionSource.DOCUMENT_HEADINGS, fields);
    }

    // ── Rule 1: the key-value header block ──────────────────────────────────

    private record HeaderFields(Optional<ExtractedField> roleTitle, Optional<ExtractedField> department,
                                Optional<ExtractedField> location, Optional<String> employmentTypeHint) {}

    private HeaderFields readHeaderBlock(String text) {
        ExtractedField roleTitle = null;
        ExtractedField department = null;
        ExtractedField location = null;
        String employmentTypeHint = null;

        Matcher matcher = HEADER_LINE.matcher(text);
        while (matcher.find()) {
            String label = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = valueOf(matcher.group(2));
            if (value.isEmpty()) {
                continue;
            }
            String snippet = matcher.group().trim();
            if (roleTitle == null && isTitleLabel(label)) {
                roleTitle = new ExtractedField("roleTitle", value, ProposalConfidence.MEDIUM, snippet);
            } else if (department == null && isDepartmentLabel(label)) {
                department = new ExtractedField("department", value, ProposalConfidence.MEDIUM, snippet);
            } else if (location == null && isLocationLabel(label)) {
                location = new ExtractedField("location", value, ProposalConfidence.MEDIUM, snippet);
            } else if (employmentTypeHint == null && isEmploymentTypeLabel(label)) {
                employmentTypeHint = value;
            }
        }
        return new HeaderFields(Optional.ofNullable(roleTitle), Optional.ofNullable(department),
                Optional.ofNullable(location), Optional.ofNullable(employmentTypeHint));
    }

    /**
     * The captured remainder of a header line, cut at the first wide gap — the point a table's next
     * column begins. Without this, a row like {@code "Job Title    CEO                Department"}
     * would capture the neighbouring column's label as part of this one's value.
     */
    private static String valueOf(String captured) {
        Matcher gap = COLUMN_GAP.matcher(captured);
        return (gap.find() ? captured.substring(0, gap.start()) : captured).trim();
    }

    private static boolean isTitleLabel(String label) {
        return label.equals("job title") || label.equals("position")
                || label.equals("role") || label.equals("title");
    }

    private static boolean isDepartmentLabel(String label) {
        return label.equals("department") || label.equals("division") || label.equals("function");
    }

    private static boolean isLocationLabel(String label) {
        return label.equals("location") || label.equals("based in");
    }

    private static boolean isEmploymentTypeLabel(String label) {
        return label.equals("employment type") || label.equals("contract type");
    }

    // ── Rule 2: the bulleted responsibilities section ───────────────────────

    /**
     * Finds the responsibilities heading, then groups every line beneath it into items until the next
     * section heading. A line starts a new item when it carries an explicit bullet marker, when the
     * line before it was blank, or — only once the section's own first line establishes a real hanging
     * indent above column zero — when its indentation returns to that opening level. The indent check
     * is guarded that way because a real PDF often extracts with <b>no</b> indentation on either a
     * bullet or its wrapped continuation, carrying only the bullet glyph itself as the boundary; an
     * unconditional indent comparison would then read every continuation line as indented "no deeper
     * than" a zero baseline and split each wrapped line into its own item.
     */
    private List<ExtractedField> readResponsibilities(String text) {
        String[] lines = text.split("\n", -1);
        int start = sectionStart(lines);
        if (start < 0) {
            return List.of();
        }

        List<ExtractedField> responsibilities = new ArrayList<>();
        StringBuilder current = null;
        Integer baselineIndent = null;
        boolean previousBlank = true;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                previousBlank = true;
                continue;
            }
            if (current != null && isSectionHeading(line)) {
                break;
            }

            int indent = indentOf(line);
            if (baselineIndent == null) {
                baselineIndent = indent;
            }
            Matcher bullet = BULLET_LINE.matcher(line);
            boolean startsNewItem = bullet.matches() || previousBlank
                    || (baselineIndent > 0 && indent <= baselineIndent);
            String content = bullet.matches() ? bullet.group(1).trim() : line.trim();

            if (startsNewItem) {
                if (current != null) {
                    responsibilities.add(itemOf(current.toString()));
                }
                current = new StringBuilder(content);
            } else {
                current.append(' ').append(content);
            }
            previousBlank = false;
        }
        if (current != null) {
            responsibilities.add(itemOf(current.toString()));
        }
        return responsibilities;
    }

    private static int sectionStart(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() <= HEADING_LINE_MAX_LENGTH
                    && RESPONSIBILITIES_KEYWORD.matcher(lines[i]).find()) {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean isSectionHeading(String line) {
        String trimmed = line.trim();
        return !BULLET_LINE.matcher(line).matches() && SECTION_HEADING.matcher(trimmed).matches();
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static ExtractedField itemOf(String text) {
        String trimmed = text.trim();
        return new ExtractedField("responsibility", trimmed, ProposalConfidence.MEDIUM, trimmed);
    }

    // ── Rule 3: employment type by keyword ───────────────────────────────────

    private static Optional<ExtractedField> readEmploymentType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        EmploymentType type = null;
        // "Permanent" checked ahead of "contract" so "this is a permanent contract" resolves as
        // permanent rather than fixed-term on the word "contract" alone.
        if (lower.contains("permanent")) {
            type = EmploymentType.FULL_TIME_PERMANENT;
        } else if (lower.contains("part time") || lower.contains("part-time")) {
            type = EmploymentType.PART_TIME;
        } else if (lower.contains("fixed term") || lower.contains("fixed-term") || lower.contains("contract")) {
            type = EmploymentType.FIXED_TERM_CONTRACT;
        } else if (lower.contains("interim")) {
            type = EmploymentType.INTERIM;
        } else if (lower.contains("retained") || lower.contains("retainer")) {
            type = EmploymentType.RETAINED_ADVISORY;
        }
        return type == null
                ? Optional.empty()
                // The keyword search runs over a hint value or the whole document, neither of which
                // is a single sentence — so no snippet is offered here rather than one spanning pages.
                : Optional.of(new ExtractedField("employmentType", type.name(), ProposalConfidence.MEDIUM, null));
    }

    // ── Rule 4: seniority, by reusing the shipped template catalog ──────────

    private Optional<ExtractedField> readSeniority(UUID workspaceId, String roleTitle) {
        return templates.matching(workspaceId, roleTitle).map(template -> {
            ProposalConfidence confidence = FALLBACK_TEMPLATE_CODE.equals(template.getCode())
                    ? ProposalConfidence.LOW
                    : ProposalConfidence.MEDIUM;
            return new ExtractedField("seniority", template.getSeniority().name(), confidence, null);
        });
    }
}
