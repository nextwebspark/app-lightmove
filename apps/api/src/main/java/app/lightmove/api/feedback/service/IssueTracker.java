package app.lightmove.api.feedback.service;

import app.lightmove.api.feedback.model.IssueDraft;
import app.lightmove.api.feedback.model.PublishedIssue;

/**
 * Where a report goes to become somebody's work item.
 *
 * <p>A port in the same sense as {@code EmailSender}: one method, two implementations, and the choice
 * made once in {@link app.lightmove.api.feedback.config.IssueTrackerConfig} from configuration. Hosting
 * the attachments is the implementation's problem rather than the composer's, because a tracker that
 * has somewhere to put an image and one that does not should not force the caller to care.
 *
 * <p>Implementations may throw. {@code FeedbackService} catches, because a tracker's bad minute must
 * not lose a tester's report.
 */
public interface IssueTracker {

    PublishedIssue publish(IssueDraft draft);
}
