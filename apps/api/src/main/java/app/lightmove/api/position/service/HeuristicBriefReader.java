package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.constant.ProposalOrigin;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ProposedPositionDetails;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
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
 * Reads step one's fields out of a position description's conventional structure with no model call —
 * the fallback when the model cannot be reached or is blocked.
 *
 * <p>Never a seed for the model: a first draft would anchor it into confirming a wrong title.
 */
@Service
@RequiredArgsConstructor
public class HeuristicBriefReader {

    /**
     * Label and value separated by a colon or a run of two-or-more spaces or a tab — the gap is how a
     * PDF's key-value table survives extraction ({@code JD_CEO.pdf}'s "Job Title    CEO").
     */
    private static final Pattern HEADER_LINE = Pattern.compile(
            "(?im)^\\s*(Job Title|Position|Role|Title|Company|Department|Division|Function|Location|"
                    + "Based in|Reports to|Reporting to|Employment Type|Contract Type|Grade)"
                    + "\\s*(?::\\s*|[ \\t]{2,})(.+?)\\s*$");

    /** Where a header's captured value spills into the next column's label across a wide gap. */
    private static final Pattern COLUMN_GAP = Pattern.compile("[ \\t]{2,}");

    /**
     * Matched anywhere in a line, not the whole line, because documents phrase it as a lead-in sentence;
     * {@link #HEADING_LINE_MAX_LENGTH} keeps an ordinary paragraph from matching.
     */
    private static final Pattern RESPONSIBILITIES_KEYWORD = Pattern.compile(
            "(?i)\\b(key focus areas|core responsibilities|responsibilities|accountabilities|duties|"
                    + "the position)\\b");
    private static final int HEADING_LINE_MAX_LENGTH = 150;

    private static final Pattern SECTION_HEADING = Pattern.compile("^[A-Z][A-Za-z /&]{1,45}:?$");

    /** Any short run of symbols counts as a bullet: a PDF's bullet glyph rarely survives extraction as itself. */
    private static final Pattern BULLET_LINE = Pattern.compile(
            "^\\s*(?:[^\\w\\s]{1,2}|\\d+[.)]|[a-zA-Z][.)])\\s+(.+?)\\s*$");

    private final PositionTemplateService templates;

    public ProposedPositionDetails propose(UUID workspaceId, String documentText) {
        List<ExtractedField> fields = new ArrayList<>();

        HeaderFields header = readHeaderBlock(documentText);
        header.roleTitle().ifPresent(fields::add);
        header.department().ifPresent(fields::add);
        header.location().ifPresent(fields::add);
        fields.addAll(readResponsibilities(documentText));
        Optional<String> employmentTypeHint = header.employmentTypeHint();
        readEmploymentType(employmentTypeHint.orElse(documentText), employmentTypeHint.isPresent())
                .ifPresent(fields::add);
        header.roleTitle().ifPresent(title ->
                readSeniority(workspaceId, title.value()).ifPresent(fields::add));

        return new ProposedPositionDetails(ExtractionSource.DOCUMENT_HEADINGS, fields);
    }

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
                roleTitle = new ExtractedField("roleTitle", value, ProposalConfidence.MEDIUM, snippet,
                        ProposalOrigin.DOCUMENT);
            } else if (department == null && isDepartmentLabel(label)) {
                department = new ExtractedField("department", value, ProposalConfidence.MEDIUM, snippet,
                        ProposalOrigin.DOCUMENT);
            } else if (location == null && isLocationLabel(label)) {
                location = new ExtractedField("location", value, ProposalConfidence.MEDIUM, snippet,
                        ProposalOrigin.DOCUMENT);
            } else if (employmentTypeHint == null && isEmploymentTypeLabel(label)) {
                employmentTypeHint = value;
            }
        }
        return new HeaderFields(Optional.ofNullable(roleTitle), Optional.ofNullable(department),
                Optional.ofNullable(location), Optional.ofNullable(employmentTypeHint));
    }

    /** Cut at the first wide gap, where a table's next column (and its label) begins. */
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

    /**
     * Groups the lines under the responsibilities heading into items until the next heading. A line
     * starts an item on a bullet, after a blank line, or on returning to the opening indent — the last
     * only when that indent is above zero, since a PDF often extracts bullets and their wrapped
     * continuations both at column zero, which would split every wrapped line.
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
            boolean isBullet = bullet.matches();
            boolean startsNewItem = isBullet || previousBlank
                    || (baselineIndent > 0 && indent <= baselineIndent);
            String content = isBullet ? bullet.group(1).trim() : line.trim();

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
        return new ExtractedField("responsibility", trimmed, ProposalConfidence.MEDIUM, trimmed,
                ProposalOrigin.DOCUMENT);
    }

    /** Without a header hint this is an unanchored search of the whole document, so it earns only {@code LOW}. */
    private static Optional<ExtractedField> readEmploymentType(String text, boolean fromHeaderHint) {
        String lower = text.toLowerCase(Locale.ROOT);
        EmploymentType type = null;
        // Ordering matters: "permanent" and "temporary" are checked ahead of the weaker "contract".
        if (lower.contains("permanent")) {
            type = EmploymentType.FULL_TIME_PERMANENT;
        } else if (lower.contains("part time") || lower.contains("part-time")) {
            type = EmploymentType.PART_TIME;
        } else if (lower.contains("temporary")) {
            type = EmploymentType.TEMPORARY;
        } else if (lower.contains("fixed term") || lower.contains("fixed-term") || lower.contains("contract")) {
            type = EmploymentType.FIXED_TERM_CONTRACT;
        } else if (lower.contains("interim")) {
            type = EmploymentType.INTERIM;
        } else if (lower.contains("retained") || lower.contains("retainer")) {
            type = EmploymentType.RETAINED_ADVISORY;
        }
        if (type == null) {
            return Optional.empty();
        }
        ProposalConfidence confidence = fromHeaderHint ? ProposalConfidence.MEDIUM : ProposalConfidence.LOW;
        return Optional.of(new ExtractedField("employmentType", type.name(), confidence, null,
                ProposalOrigin.DOCUMENT));
    }

    private Optional<ExtractedField> readSeniority(UUID workspaceId, String roleTitle) {
        return templates.matching(workspaceId, roleTitle).map(template -> {
            ProposalConfidence confidence = PositionTemplateService.FALLBACK_CODE.equals(template.getCode())
                    ? ProposalConfidence.LOW
                    : ProposalConfidence.MEDIUM;
            return new ExtractedField("seniority", template.getSeniority().name(), confidence, null,
                    ProposalOrigin.TEMPLATE);
        });
    }
}
