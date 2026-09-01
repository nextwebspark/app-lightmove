package app.lightmove.api.feedback.service;

import app.lightmove.api.feedback.model.FeedbackAttachment;
import app.lightmove.api.feedback.model.IssueDraft;
import app.lightmove.api.feedback.model.PublishedIssue;
import lombok.extern.slf4j.Slf4j;

/**
 * Prints the issue it would have filed. The default, and the reason a fresh clone can drive the whole
 * widget — capture, form, submit, confirmation — without anyone creating a GitHub token first.
 *
 * <p>The images are named and sized rather than dumped: a base64 screenshot in a log is several
 * hundred lines of nothing anyone can read.
 */
@Slf4j
public class LoggingIssueTracker implements IssueTracker {

    @Override
    public PublishedIssue publish(IssueDraft draft) {
        String images = draft.attachments().isEmpty()
                ? "none"
                : draft.attachments().stream()
                        .map(FeedbackAttachment::fileName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("none");

        log.info("""

                ─── UAT report (not filed — no issue tracker configured) ───────────────────
                {}
                labels: {}
                images: {}

                {}
                ───────────────────────────────────────────────────────────────────────────
                """, draft.title(), draft.labels(), images, draft.body());

        return PublishedIssue.unpublished();
    }
}
