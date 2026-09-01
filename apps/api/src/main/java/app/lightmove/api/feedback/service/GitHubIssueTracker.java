package app.lightmove.api.feedback.service;

import app.lightmove.api.core.config.GitHubFeedbackSettings;
import app.lightmove.api.feedback.model.FeedbackAttachment;
import app.lightmove.api.feedback.model.IssueDraft;
import app.lightmove.api.feedback.model.PublishedIssue;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * Files the report as a GitHub issue, screenshots and all.
 *
 * <p><b>Why the images take a detour.</b> GitHub's REST API has no endpoint for issue attachments —
 * the uploader the web UI drives is private and a token cannot reach it — and a {@code data:} URI in a
 * body is stripped by GitHub's markdown sanitiser. So an image needs a real URL before it can appear
 * in an issue, and the cheapest one that keeps the whole feature inside GitHub is a commit: each file
 * is written to a branch nothing builds from, and the issue embeds the raw URL that commit produced.
 *
 * <p>The branch is created <b>orphaned</b>, with no parent commit, so it carries the attachments and
 * none of the repository's history. A branch cut from the default one would work identically and drag
 * the entire tree along for the ride.
 *
 * <p>An attachment that fails to upload does not fail the report. The issue is filed either way with a
 * line saying an image was lost — a report that arrives without its screenshot is worth far more than
 * one that never arrives.
 */
@Slf4j
public class GitHubIssueTracker implements IssueTracker {

    /** Short: a slow tracker must not hold a request thread while a tester waits on a bug report. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private static final String API_VERSION = "2022-11-28";

    private final RestClient client;
    private final GitHubFeedbackSettings settings;

    /**
     * Whether the attachment branch is known to exist. Only ever set true, and a wrong {@code false}
     * costs one extra HEAD-shaped request — so the race between two instances creating it at once is
     * resolved by GitHub's own 422, not by locking.
     */
    private volatile boolean attachmentBranchReady;

    public GitHubIssueTracker(GitHubFeedbackSettings settings, RestClient.Builder builder) {
        this.settings = settings;
        this.client = builder
                .baseUrl(settings.baseUrl())
                .defaultHeader("Authorization", "Bearer " + settings.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .build();
    }

    @Override
    public PublishedIssue publish(IssueDraft draft) {
        String body = draft.body() + attachmentSection(draft.attachments());

        GitHubIssue issue = client.post()
                .uri("/repos/{owner}/{repo}/issues", settings.owner(), settings.repositoryName())
                .body(Map.of(
                        "title", draft.title(),
                        "body", body,
                        "labels", draft.labels()))
                .retrieve()
                .body(GitHubIssue.class);

        if (issue == null) {
            throw new IllegalStateException("GitHub accepted the issue but returned no body");
        }
        log.info("Filed UAT issue #{} in {}", issue.number(), settings.repository());
        return PublishedIssue.at(issue.number(), issue.htmlUrl());
    }

    /**
     * The images, as markdown, after everything the triager reads first.
     *
     * <p>Each upload is attempted independently: one oversized file must not cost the report the
     * screenshot beside it.
     */
    private String attachmentSection(List<FeedbackAttachment> attachments) {
        if (attachments.isEmpty()) {
            return "";
        }

        StringBuilder section = new StringBuilder("\n### Attachments\n\n");
        for (FeedbackAttachment attachment : attachments) {
            try {
                String url = upload(attachment);
                section.append("**").append(attachment.origin().label()).append("** — ")
                        .append(attachment.fileName()).append("\n\n")
                        .append("![").append(attachment.fileName()).append("](").append(url).append(")\n\n");
            } catch (RuntimeException ex) {
                log.error("Could not upload {} to {}", attachment.fileName(), settings.repository(), ex);
                section.append("_An image (").append(attachment.origin().label())
                        .append(") could not be uploaded and has been lost._\n\n");
            }
        }
        return section.toString();
    }

    /** Commits one image to the attachment branch and answers the raw URL markdown can render. */
    private String upload(FeedbackAttachment attachment) {
        ensureAttachmentBranch();

        // The stored name is ours, not the tester's. A caller-supplied filename would be interpolated
        // straight into a repository path, where "../" names a directory and a stray character names
        // a file GitHub will not serve — so the only thing carried over from theirs is the extension,
        // and that is derived from the content type the allowlist already approved.
        String directory = Instant.now().toString().substring(0, 10);
        String fileName = UUID.randomUUID() + "." + extensionFor(attachment.contentType());

        GitHubContentWrite written = client.put()
                // pathSegment, not a URI template: a template variable holding "a/b/c" is expanded
                // with its slashes percent-encoded, so the path this file needs — three segments deep
                // — would arrive at GitHub as one segment named "attachments%2F…" and 404.
                .uri(builder -> builder
                        .pathSegment("repos", settings.owner(), settings.repositoryName(), "contents",
                                "attachments", directory, fileName)
                        .build())
                .body(Map.of(
                        "message", "UAT attachment: " + attachment.fileName(),
                        "content", Base64.getEncoder().encodeToString(attachment.content()),
                        "branch", settings.attachmentBranch()))
                .retrieve()
                .body(GitHubContentWrite.class);

        if (written == null || written.content() == null || written.content().downloadUrl() == null) {
            throw new IllegalStateException("GitHub stored the file but returned no download URL");
        }
        return written.content().downloadUrl();
    }

    /**
     * Creates the attachment branch on first use — blob, tree, parentless commit, ref.
     *
     * <p>Four calls rather than one because there is no "create an empty branch" endpoint: a ref needs
     * a commit, a commit needs a tree, and a tree needs at least one entry. The README that entry
     * holds is the one thing on the branch that a human arriving there wants to read.
     */
    private void ensureAttachmentBranch() {
        if (attachmentBranchReady) {
            return;
        }
        String branch = settings.attachmentBranch();

        if (branchExists(branch)) {
            attachmentBranchReady = true;
            return;
        }

        try {
            String blob = post("/repos/{owner}/{repo}/git/blobs", Map.of(
                    "content", Base64.getEncoder().encodeToString(readmeFor(branch).getBytes(StandardCharsets.UTF_8)),
                    "encoding", "base64"), GitHubSha.class).sha();

            String tree = post("/repos/{owner}/{repo}/git/trees", Map.of(
                    "tree", List.of(Map.of(
                            "path", "README.md",
                            "mode", "100644",
                            "type", "blob",
                            "sha", blob))), GitHubSha.class).sha();

            // No parents: an orphan commit, so the branch starts empty of the repository's history
            // rather than carrying a copy of the whole tree behind every screenshot.
            String commit = post("/repos/{owner}/{repo}/git/commits", Map.of(
                    "message", "Start the UAT attachment branch",
                    "tree", tree,
                    "parents", List.of()), GitHubSha.class).sha();

            post("/repos/{owner}/{repo}/git/refs", Map.of(
                    "ref", "refs/heads/" + branch,
                    "sha", commit), GitHubSha.class);

            log.info("Created attachment branch {} in {}", branch, settings.repository());
        } catch (RuntimeException ex) {
            // Two instances filing at once both find no branch and both create one; the loser gets a
            // 422. That is success, not failure — but only if the branch really is there now, so this
            // re-reads rather than assuming.
            if (!branchExists(branch)) {
                throw ex;
            }
        }
        attachmentBranchReady = true;
    }

    private boolean branchExists(String branch) {
        return Boolean.TRUE.equals(client.get()
                .uri(builder -> builder
                        .pathSegment("repos", settings.owner(), settings.repositoryName(),
                                "git", "ref", "heads", branch)
                        .build())
                // exchange, not retrieve: a branch that does not exist answers 404, and that is the
                // question being asked rather than a failure to propagate.
                .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()));
    }

    private <T> T post(String uri, Map<String, Object> body, Class<T> type) {
        T answer = client.post()
                .uri(uri, settings.owner(), settings.repositoryName())
                .body(body)
                .retrieve()
                .body(type);
        if (answer == null) {
            throw new IllegalStateException("GitHub answered " + uri + " with no body");
        }
        return answer;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private String readmeFor(String branch) {
        return """
                # UAT attachments

                Screenshots from LightMove's in-app bug reporter live on this branch, so the issues
                they belong to have an image URL to embed. Nothing here is code, nothing builds from
                it, and it is safe to prune once the issues referencing it are closed.

                Branch: `%s`
                """.formatted(branch);
    }

    // Package-private, and every component named explicitly: these are only ever built by Jackson from
    // GitHub's own JSON, and a private record plus an inferred name is two ways for that to fail
    // silently on a runtime that has neither reflective access nor `-parameters`.

    /** Only the two fields the widget needs back; GitHub sends about sixty. */
    record GitHubIssue(@JsonProperty("number") Integer number,
                       @JsonProperty("html_url") String htmlUrl) {}

    record GitHubSha(@JsonProperty("sha") String sha) {}

    record GitHubContentWrite(@JsonProperty("content") GitHubContentFile content) {}

    record GitHubContentFile(@JsonProperty("download_url") String downloadUrl) {}
}
