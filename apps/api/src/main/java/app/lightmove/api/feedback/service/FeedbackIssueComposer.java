package app.lightmove.api.feedback.service;

import app.lightmove.api.core.config.GitHubFeedbackSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.feedback.constant.FeedbackKind;
import app.lightmove.api.feedback.model.FeedbackContext;
import app.lightmove.api.feedback.model.FeedbackReport;
import app.lightmove.api.feedback.model.FeedbackReporter;
import app.lightmove.api.feedback.model.IssueDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns a report into the issue a triager reads: a title, a body, and the labels that route it.
 *
 * <p>Everything a tester typed is <b>untrusted text on its way into somebody else's rendered
 * markdown</b>, and this is the only place that is dealt with. GitHub sanitises HTML, so the risk is
 * not script — it is the two things markdown does that text should not: {@code @name} notifies a real
 * person, and a control character can break the table the surrounding rows depend on. Both are
 * neutralised here rather than at each interpolation, so a new section cannot forget.
 */
@Component
public class FeedbackIssueComposer {

    /** An {@code @} that GitHub would resolve to a user or team. A bare @ in prose is left alone. */
    private static final Pattern MENTION = Pattern.compile("@(?=[A-Za-z0-9][A-Za-z0-9-]*)");

    /**
     * An empty HTML comment between the {@code @} and the name. It renders as nothing, so the reader
     * still sees {@code @someone} — but GitHub's mention parser no longer matches, so nobody is paged
     * into a stranger's bug report.
     */
    private static final String MENTION_BREAK = "@<!---->";

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");

    private final GitHubFeedbackSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public FeedbackIssueComposer(LightMoveProperties properties) {
        this.settings = properties.feedback().github();
    }

    public IssueDraft compose(FeedbackReport report) {
        return new IssueDraft(titleOf(report), bodyOf(report), labelsOf(report), report.attachments());
    }

    private String titleOf(FeedbackReport report) {
        String kind = report.kind() == FeedbackKind.BUG ? "Bug" : "Feature";
        return "[UAT][%s] %s".formatted(kind, oneLine(report.title()));
    }

    private List<String> labelsOf(FeedbackReport report) {
        List<String> labels = new ArrayList<>(settings.labels());
        String kindLabel = report.kind() == FeedbackKind.BUG
                ? settings.bugLabel()
                : settings.featureRequestLabel();
        if (!kindLabel.isBlank() && !labels.contains(kindLabel)) {
            labels.add(kindLabel);
        }
        return List.copyOf(labels);
    }

    private String bodyOf(FeedbackReport report) {
        StringBuilder body = new StringBuilder();

        body.append("> Filed from LightMove's in-app UAT reporter. Everything below the reporter's own\n")
                .append("> words is collected automatically and has not been verified.\n\n");

        body.append(report.kind() == FeedbackKind.BUG ? "### What happened\n\n" : "### What is wanted\n\n")
                .append(prose(report.message()))
                .append("\n\n");

        if (report.stepsToReproduce() != null && !report.stepsToReproduce().isBlank()) {
            body.append("### Steps to reproduce\n\n")
                    .append(prose(report.stepsToReproduce()))
                    .append("\n\n");
        }

        body.append("### Reporter\n\n");
        appendReporter(body, report.reporter());

        body.append("\n### Severity\n\n")
                .append("`").append(report.severity().name()).append("`\n");

        body.append("\n### Environment\n\n");
        appendEnvironment(body, report.context());

        return body.toString();
    }

    private void appendReporter(StringBuilder body, FeedbackReporter reporter) {
        body.append("| | |\n|---|---|\n");
        row(body, "Name", reporter.displayName());
        row(body, "Email", reporter.email() == null ? "not given" : reporter.email());
        // Stated plainly because the two cases are worth different amounts. A signed-in reporter's
        // identity was read off a token we signed; an anonymous one typed whatever they liked.
        row(body, "Identity", reporter.authenticated()
                ? "signed in — read from the session, not the form"
                : "not signed in — address is unverified");
        if (reporter.userId() != null) {
            row(body, "User id", "`" + reporter.userId() + "`");
        }
        if (reporter.workspaceId() != null) {
            row(body, "Workspace id", "`" + reporter.workspaceId() + "`");
        }
        if (!reporter.roles().isEmpty()) {
            row(body, "Workspace roles", String.join(", ", reporter.roles()));
        }
    }

    private void appendEnvironment(StringBuilder body, FeedbackContext context) {
        body.append("| | |\n|---|---|\n");
        row(body, "Page", context.pageUrl());
        row(body, "Viewport", context.viewport());
        row(body, "Screen", context.screenSize());
        row(body, "Pixel ratio", context.devicePixelRatio());
        row(body, "Theme", context.theme());
        row(body, "Language", context.language());
        row(body, "Timezone", context.timezone());
        row(body, "Browser clock", context.reportedAt());
        row(body, "User agent", context.userAgent());
    }

    private void row(StringBuilder body, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        body.append("| ").append(label).append(" | ").append(cell(value)).append(" |\n");
    }

    /** Free text, kept readable: paragraphs survive, mentions and control characters do not. */
    private static String prose(String text) {
        return neutralise(text).strip();
    }

    /**
     * A table cell, where a raw {@code |} would end the column and a newline would end the row —
     * turning one pasted stack trace into a table the rest of the report is missing from.
     */
    private static String cell(String value) {
        return oneLine(value).replace("|", "\\|");
    }

    private static String oneLine(String value) {
        return neutralise(value).replaceAll("\\s+", " ").strip();
    }

    private static String neutralise(String value) {
        return MENTION.matcher(CONTROL_CHARACTERS.matcher(value).replaceAll(""))
                .replaceAll(MENTION_BREAK);
    }
}
