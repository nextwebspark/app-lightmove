package app.lightmove.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.FeedbackSettings;
import app.lightmove.api.core.config.GitHubFeedbackSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.feedback.constant.FeedbackKind;
import app.lightmove.api.feedback.constant.FeedbackSeverity;
import app.lightmove.api.feedback.model.FeedbackContext;
import app.lightmove.api.feedback.model.FeedbackReport;
import app.lightmove.api.feedback.model.FeedbackReporter;
import app.lightmove.api.feedback.model.IssueDraft;
import app.lightmove.api.feedback.service.FeedbackIssueComposer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The issue a tester's words become.
 *
 * <p>Most of what is asserted here is about text this application did not write reaching markdown
 * somebody else renders — the reason the composer exists as its own class rather than as string
 * concatenation inside the tracker.
 */
class FeedbackIssueComposerTest {

    private final FeedbackIssueComposer composer = new FeedbackIssueComposer(properties());

    @Test
    @DisplayName("an @mention in a report does not page a real person into it")
    void neutralisesMentions() {
        IssueDraft draft = composer.compose(report("Broken", "cc @octocat and @lightmove-team please"));

        // The reader still sees "@octocat"; GitHub's mention parser no longer matches it. Getting this
        // wrong means anyone who can reach the anonymous endpoint can notify any GitHub user at will.
        assertThat(draft.body()).contains("@<!---->octocat").doesNotContain(" @octocat");
        assertThat(draft.body()).contains("@<!---->lightmove-team");
    }

    @Test
    @DisplayName("a pipe or a newline in a value does not break the table around it")
    void escapesTableCells() {
        FeedbackReport report = new FeedbackReport(
                FeedbackKind.BUG, FeedbackSeverity.HIGH, "Broken", "It broke", null,
                FeedbackReporter.anonymous("tester@example.com"),
                new FeedbackContext("/projects | /strategy", "Mozilla\n5.0", "390x844",
                        null, null, null, null, "dark", null),
                List.of());

        String body = composer.compose(report).body();

        assertThat(body).contains("| Page | /projects \\| /strategy |");
        assertThat(body).contains("| User agent | Mozilla 5.0 |");
    }

    @Test
    @DisplayName("the title says what kind of report it is, on one line")
    void titlesByKind() {
        assertThat(composer.compose(report("Saving loses step 3", "…")).title())
                .isEqualTo("[UAT][Bug] Saving loses step 3");

        FeedbackReport wish = new FeedbackReport(
                FeedbackKind.FEATURE_REQUEST, FeedbackSeverity.LOW, "Export\nthe shortlist", "…", null,
                FeedbackReporter.anonymous(null), emptyContext(), List.of());
        assertThat(composer.compose(wish).title()).isEqualTo("[UAT][Feature] Export the shortlist");
    }

    @Test
    @DisplayName("the kind decides the second label; the configured one is always applied")
    void labelsByKind() {
        assertThat(composer.compose(report("Broken", "…")).labels()).containsExactly("uat", "bug");

        FeedbackReport wish = new FeedbackReport(
                FeedbackKind.FEATURE_REQUEST, FeedbackSeverity.LOW, "Export", "…", null,
                FeedbackReporter.anonymous(null), emptyContext(), List.of());
        assertThat(composer.compose(wish).labels()).containsExactly("uat", "enhancement");
    }

    @Test
    @DisplayName("a signed-in reporter is marked as one, and an anonymous address as unverified")
    void distinguishesTheTwoKindsOfReporter() {
        FeedbackReport anonymous = report("Broken", "…");
        assertThat(composer.compose(anonymous).body()).contains("address is unverified");

        FeedbackReport signedIn = new FeedbackReport(
                FeedbackKind.BUG, FeedbackSeverity.MEDIUM, "Broken", "…", null,
                new FeedbackReporter(true, UUID.randomUUID(), UUID.randomUUID(),
                        "Sara Haddad", "sara@nextwebspark.com", null, List.of("ADMIN")),
                emptyContext(), List.of());

        String body = composer.compose(signedIn).body();
        assertThat(body).contains("Sara Haddad").contains("read from the session");
        assertThat(body).contains("| Workspace roles | ADMIN |");
    }

    @Test
    @DisplayName("steps to reproduce appear only when there are some")
    void omitsEmptySections() {
        assertThat(composer.compose(report("Broken", "…")).body())
                .doesNotContain("Steps to reproduce");

        FeedbackReport withSteps = new FeedbackReport(
                FeedbackKind.BUG, FeedbackSeverity.MEDIUM, "Broken", "…", "1. Open it\n2. Watch",
                FeedbackReporter.anonymous(null), emptyContext(), List.of());
        assertThat(composer.compose(withSteps).body()).contains("1. Open it\n2. Watch");
    }

    private static FeedbackReport report(String title, String message) {
        return new FeedbackReport(FeedbackKind.BUG, FeedbackSeverity.MEDIUM, title, message, null,
                FeedbackReporter.anonymous(null), emptyContext(), List.of());
    }

    private static FeedbackContext emptyContext() {
        return new FeedbackContext(null, null, null, null, null, null, null, null, null);
    }

    /** Only the feedback branch is read, so the rest of the tree is left out rather than faked. */
    private static LightMoveProperties properties() {
        GitHubFeedbackSettings github = new GitHubFeedbackSettings(
                "https://api.github.com", "nextwebspark/app-lightmove", "", "uat-attachments",
                List.of("uat"), "bug", "enhancement");
        FeedbackSettings feedback = new FeedbackSettings(
                true, true, 10, 140, 5000, 4, 5_242_880L,
                List.of("image/png"), github);
        return new LightMoveProperties(null, null, null, null, null, null, feedback);
    }
}
