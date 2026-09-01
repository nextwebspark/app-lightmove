package app.lightmove.api.feedback.model;

import java.util.List;
import java.util.UUID;

/**
 * Who filed a report, as far as the server can tell.
 *
 * <p>Split cleanly in two, and the split is the point. When the caller holds a session, every field
 * here is <b>server-derived</b> — read from the authenticated principal and the membership row, never
 * from the request. When they do not, the only thing on offer is a typed address, which proves nothing
 * and is labelled as unverified everywhere it is shown.
 *
 * <p>A tester hitting a bug on the login screen has no session, which is exactly when a bug report is
 * most valuable, so the anonymous case is a first-class one rather than a degraded one.
 */
public record FeedbackReporter(
        boolean authenticated,
        UUID userId,
        UUID workspaceId,
        String fullName,
        String email,
        String workspaceName,
        List<String> roles
) {

    public static FeedbackReporter anonymous(String claimedEmail) {
        return new FeedbackReporter(false, null, null, null, claimedEmail, null, List.of());
    }

    /** What the issue calls them. Falls back through the fields that might be missing. */
    public String displayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "Anonymous tester";
    }
}
