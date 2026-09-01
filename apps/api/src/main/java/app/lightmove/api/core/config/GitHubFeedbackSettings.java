package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where a UAT report becomes a GitHub issue — {@code lightmove.feedback.github.*}.
 *
 * <p>Everything here is configuration rather than a constant because the tracker is the one part of
 * the feature that must move: UAT files against one repository, the engineering backlog may live in
 * another, and a fine-grained token is rotated on a schedule nobody wants to redeploy code for.
 *
 * <p>An unset {@link #token} is not a misconfiguration — it is the local default. The tracker falls
 * back to logging the issue it would have filed, so a fresh clone runs the whole widget end to end
 * without anyone creating a personal access token first.
 */
public record GitHubFeedbackSettings(
        @DefaultValue("https://api.github.com") String baseUrl,

        /** {@code owner/name}. Blank disables publishing, exactly as a blank token does. */
        @DefaultValue("") String repository,

        /** A fine-grained personal access token with Issues: read and write, Contents: read and write. */
        @DefaultValue("") String token,

        /**
         * The branch screenshots are committed to, so the issue can link an image the reader can see.
         *
         * <p>GitHub's REST API has no endpoint for issue attachments — the one the web UI uses is not
         * public — so an image needs a URL of its own before it can appear in a body. Committing it to
         * a branch nothing builds from is the cheapest durable answer that keeps the whole feature
         * inside GitHub. The branch is created as an <b>orphan</b> on first use: it carries the
         * attachments and no code history at all.
         */
        @DefaultValue("uat-attachments") String attachmentBranch,

        /** Applied to every issue this feature files, whatever the report says it is. */
        @DefaultValue("uat") List<String> labels,

        @DefaultValue("bug") String bugLabel,
        @DefaultValue("enhancement") String featureRequestLabel
) {

    public GitHubFeedbackSettings {
        // @DefaultValue on a List binds an operator's empty override to [""], not to [] — the trap
        // that once emptied the consumer-domain blocklist. A blank label is refused by GitHub, so it
        // is refused here instead, where the message can say which key is wrong.
        if (labels.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "lightmove.feedback.github.labels must not contain a blank entry");
        }
        if (!repository.isBlank() && repository.split("/").length != 2) {
            throw new IllegalArgumentException(
                    "lightmove.feedback.github.repository must be owner/name, but was " + repository);
        }
        labels = List.copyOf(labels);
    }

    /** True when this deployment has both a repository to file against and a credential to do it with. */
    public boolean publishable() {
        return !repository.isBlank() && !token.isBlank();
    }

    public String owner() {
        return repository.split("/")[0];
    }

    public String repositoryName() {
        return repository.split("/")[1];
    }
}
