package app.lightmove.api.core.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The in-app bug and feature reporter — {@code lightmove.feedback.*}.
 *
 * <p>Its endpoint is the only write in the application a caller with no session may make, which is
 * the whole reason these numbers exist: a tester hitting a bug on the login screen has no account to
 * report it from, and a reporter nobody can reach is a report nobody files. Every ceiling here is
 * therefore a fence around an anonymous endpoint rather than an ergonomic preference.
 */
public record FeedbackSettings(

        /** Off refuses the endpoint outright, for a deployment that is past UAT. */
        @DefaultValue("true") boolean enabled,

        /**
         * Whether a caller with no session may file. Off narrows the endpoint to signed-in users and
         * leaves the widget's pre-login launcher reporting that reports are closed.
         */
        @DefaultValue("true") boolean allowAnonymous,

        /** Per hour, counted twice over: once against the caller's IP, once against the reporter. */
        @DefaultValue("10") int reportsPerHour,

        @DefaultValue("140") int maxTitleLength,
        @DefaultValue("5000") int maxMessageLength,

        /** The captured screenshot counts towards this, so the default leaves room for three uploads. */
        @DefaultValue("4") int maxAttachments,

        @DefaultValue("5242880") long maxAttachmentSizeBytes,

        /**
         * Checked server-side: a multipart part's declared content type is a claim, not evidence.
         * Images only — the report is a description plus what the tester saw, never a payload.
         */
        @DefaultValue({"image/png", "image/jpeg", "image/webp", "image/gif"})
        List<String> allowedAttachmentTypes,

        GitHubFeedbackSettings github
) {

    public FeedbackSettings {
        if (maxAttachmentSizeBytes < 1) {
            throw new IllegalArgumentException(
                    "lightmove.feedback.max-attachment-size-bytes must be positive, but was "
                            + maxAttachmentSizeBytes);
        }
        // @DefaultValue on a List binds an operator's empty override to [""], not to [] — an allowlist
        // of one blank string accepts nothing, so it is refused loudly here rather than silently
        // rejecting every screenshot the widget captures.
        if (allowedAttachmentTypes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "lightmove.feedback.allowed-attachment-types must not contain a blank entry");
        }
        allowedAttachmentTypes = List.copyOf(allowedAttachmentTypes);
    }

    public boolean allows(String contentType) {
        // Set.copyOf(...).contains(null) throws rather than answering false, so the null guard is not
        // optional here: a multipart part may arrive with no declared type at all.
        return contentType != null && Set.copyOf(allowedAttachmentTypes).contains(contentType);
    }
}
