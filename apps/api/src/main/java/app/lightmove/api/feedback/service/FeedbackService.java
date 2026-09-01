package app.lightmove.api.feedback.service;

import app.lightmove.api.core.audit.constant.FeedbackEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.FeedbackSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.feedback.constant.AttachmentOrigin;
import app.lightmove.api.feedback.constant.FeedbackSeverity;
import app.lightmove.api.feedback.dto.FeedbackContextRequest;
import app.lightmove.api.feedback.dto.FeedbackRequest;
import app.lightmove.api.feedback.dto.FeedbackResponse;
import app.lightmove.api.feedback.model.FeedbackAttachment;
import app.lightmove.api.feedback.model.FeedbackContext;
import app.lightmove.api.feedback.model.FeedbackReport;
import app.lightmove.api.feedback.model.FeedbackReporter;
import app.lightmove.api.feedback.model.IssueDraft;
import app.lightmove.api.feedback.model.PublishedIssue;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Takes a report from the in-app widget and turns it into somebody's work item.
 *
 * <p><b>Nothing is stored.</b> There is no table and no migration: the report is validated, composed
 * and handed to the issue tracker inside the one request, and the bytes are then gone. The tracker is
 * the system of record — a copy in our database would be a second inbox nobody reads, going stale
 * against the issue that is actually being worked.
 *
 * <p>Two things follow from that and are worth stating. A tracker outage loses the report, so the
 * reporter is told plainly rather than thanked; and because there is no row to grow, the ceilings on
 * message length and attachment size are about what a request may cost us, not about disk.
 *
 * <p>This is also <b>the only write in the application a caller with no session may make</b>. The
 * fences are all here: the endpoint may be switched off, anonymity may be switched off, and both
 * budgets — per IP and per reporter — are consumed on every attempt.
 */
@Slf4j
@Service
public class FeedbackService {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    private final FeedbackIssueComposer composer;
    private final IssueTracker tracker;
    private final RateLimitGuard rateLimit;
    private final AuditService audit;
    private final UserRepository users;
    private final FeedbackSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public FeedbackService(FeedbackIssueComposer composer,
                           IssueTracker tracker,
                           RateLimitGuard rateLimit,
                           AuditService audit,
                           UserRepository users,
                           LightMoveProperties properties) {
        this.composer = composer;
        this.tracker = tracker;
        this.rateLimit = rateLimit;
        this.audit = audit;
        this.users = users;
        this.settings = properties.feedback();
    }

    /**
     * Deliberately not {@code @Transactional}. The work here is one outbound HTTP call to a service
     * that can take twenty seconds to fail, and holding a database connection open across it would
     * put a stranger's bug report in the way of the connection pool the product runs on.
     */
    public FeedbackResponse submit(AuthPrincipal principal,
                                   FeedbackRequest request,
                                   MultipartFile screenshot,
                                   List<MultipartFile> uploads,
                                   HttpServletRequest httpRequest) {
        if (!settings.enabled()) {
            throw ApiException.userFacing(ErrorCode.FORBIDDEN, "Reporting is closed on this deployment");
        }
        if (principal == null && !settings.allowAnonymous()) {
            throw ApiException.userFacing(ErrorCode.FORBIDDEN, "Please sign in to send a report");
        }

        FeedbackReporter reporter = reporterOf(principal, request.reporterEmail());

        // The subject budget is the reporter, which for an anonymous caller is a typed address they
        // could change at will — so it narrows nothing on its own. The per-IP budget beside it is the
        // one that actually holds, and both are consumed either way.
        rateLimit.check("feedback", "reporter", reporterBudgetKey(reporter), httpRequest,
                settings.reportsPerHour(), RATE_LIMIT_WINDOW);

        FeedbackReport report = new FeedbackReport(
                request.kind(),
                Objects.requireNonNullElse(request.severity(), FeedbackSeverity.MEDIUM),
                bounded(request.title(), settings.maxTitleLength(), "title", "Summary"),
                bounded(request.message(), settings.maxMessageLength(), "message", "Description"),
                bounded(request.stepsToReproduce(), settings.maxMessageLength(), "stepsToReproduce", "Steps"),
                reporter,
                contextOf(request.context()),
                attachmentsOf(screenshot, uploads));

        IssueDraft draft = composer.compose(report);
        PublishedIssue issue = publish(draft, reporter, httpRequest);

        return new FeedbackResponse(issue.isPublished(), issue.number(), issue.url());
    }

    /**
     * A tracker's bad minute must not be reported to the tester as their own mistake — but it must be
     * reported. The ledger gets the cause; the reporter gets a refusal they can act on by trying again.
     */
    private PublishedIssue publish(IssueDraft draft, FeedbackReporter reporter, HttpServletRequest httpRequest) {
        try {
            PublishedIssue issue = tracker.publish(draft);
            audit.event(FeedbackEventType.FEEDBACK_SUBMITTED)
                    .actor(reporter.userId()).workspace(reporter.workspaceId()).from(httpRequest)
                    .detail("published", issue.isPublished())
                    .detail("issue", issue.number())
                    .record();
            return issue;
        } catch (RuntimeException ex) {
            log.error("Could not file a UAT report with the issue tracker", ex);
            audit.event(FeedbackEventType.FEEDBACK_PUBLISH_FAILED)
                    .failed()
                    .actor(reporter.userId()).workspace(reporter.workspaceId()).from(httpRequest)
                    .reason(ex.getClass().getSimpleName())
                    .record();
            throw ApiException.userFacing(ErrorCode.INTERNAL_ERROR,
                    "We could not file your report just now. Please try again in a moment.");
        }
    }

    /**
     * Who is reporting, and the split that matters: a signed-in reporter is described entirely from
     * their token and their user row, never from the body. A body field naming the reporter would let
     * anyone put any colleague's name on any issue.
     */
    private FeedbackReporter reporterOf(AuthPrincipal principal, String claimedEmail) {
        if (principal == null) {
            return FeedbackReporter.anonymous(claimedEmail);
        }
        String fullName = users.findById(principal.userId())
                .map(user -> user.getFullName())
                .orElse(null);
        return new FeedbackReporter(
                true,
                principal.userId(),
                principal.workspaceId(),
                fullName,
                principal.email(),
                null,
                principal.roles().stream().map(Enum::name).toList());
    }

    /** Blank for an anonymous reporter who gave no address — which is one shared bucket, by design. */
    private static String reporterBudgetKey(FeedbackReporter reporter) {
        if (reporter.userId() != null) {
            return reporter.userId().toString();
        }
        return reporter.email() == null ? "" : reporter.email();
    }

    private FeedbackContext contextOf(FeedbackContextRequest context) {
        if (context == null) {
            return new FeedbackContext(null, null, null, null, null, null, null, null, null);
        }
        return new FeedbackContext(
                context.pageUrl(),
                context.userAgent(),
                context.viewport(),
                context.screenSize(),
                context.devicePixelRatio(),
                context.language(),
                context.timezone(),
                context.theme(),
                context.reportedAt());
    }

    private List<FeedbackAttachment> attachmentsOf(MultipartFile screenshot, List<MultipartFile> uploads) {
        boolean hasCapture = screenshot != null && !screenshot.isEmpty();
        List<MultipartFile> images = uploads == null
                ? List.of()
                : uploads.stream().filter(upload -> upload != null && !upload.isEmpty()).toList();

        // Counted before a single byte is read. Reading first and refusing after would let anyone who
        // can reach this endpoint — which is anyone at all — spend the heap on a hundred images before
        // being told four was the limit.
        if (images.size() + (hasCapture ? 1 : 0) > settings.maxAttachments()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "A report may carry at most " + settings.maxAttachments() + " images");
        }

        List<FeedbackAttachment> attachments = new ArrayList<>();
        if (hasCapture) {
            attachments.add(readAttachment(screenshot, AttachmentOrigin.SCREEN_CAPTURE));
        }
        images.forEach(image -> attachments.add(readAttachment(image, AttachmentOrigin.UPLOAD)));
        return List.copyOf(attachments);
    }

    private FeedbackAttachment readAttachment(MultipartFile file, AttachmentOrigin origin) {
        if (file.getSize() > settings.maxAttachmentSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "attachment of " + file.getSize() + " bytes exceeds " + settings.maxAttachmentSizeBytes());
        }
        // The declared content type is a claim made by whatever sent the request, so the allowlist
        // decides. It also picks the extension the file is stored under, so an unrecognised type is
        // refused rather than guessed at.
        if (!settings.allows(file.getContentType())) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "rejected content type " + file.getContentType());
        }
        try {
            return new FeedbackAttachment(
                    safeFileNameOf(file.getOriginalFilename()),
                    file.getContentType(),
                    file.getBytes(),
                    origin);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read an image attached to a UAT report", e);
        }
    }

    /**
     * The original filename is caller-supplied and is interpolated into markdown, where a {@code ]} or
     * a {@code )} ends an image link early and leaves the rest of the body as visible syntax. Path
     * separators go for the usual reason. What survives is a plain name, or a generated one.
     */
    private static String safeFileNameOf(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "attachment-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String withoutPath = originalFileName.replaceAll(".*[/\\\\]", "");
        String cleaned = withoutPath.replaceAll("[\\p{Cntrl}\\[\\]()<>\"'`|]", "").trim();
        if (cleaned.isEmpty()) {
            return "attachment-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    /** Trims to the configured ceiling rather than refusing — the interesting half of a long paste is
     *  usually the start, and losing a report to a length rule is a poor trade during UAT. */
    private static String bounded(String value, int max, String field, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= max) {
            return trimmed;
        }
        log.debug("Truncated {} of a UAT report from {} to {} characters", field, trimmed.length(), max);
        return trimmed.substring(0, max) + "\n\n…" + label + " truncated at " + max + " characters.";
    }
}
