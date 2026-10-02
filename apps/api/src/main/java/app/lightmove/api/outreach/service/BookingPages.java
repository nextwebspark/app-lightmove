package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.dto.BookingPageResponse;
import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.BookingSlug;
import app.lightmove.api.outreach.model.MailboxConnected;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A consultant's booking link: {@code <web.base-url>/book/<slug>}, a public page where an executive picks a
 * time on their calendar. The slug is all an email needs, so a send never waits on the mail service. The
 * Scheduler page behind it is made at Start, and again once a reconnect commits; the public read only reads.
 */
@Service
@Slf4j
public class BookingPages {

    static final int CALL_MINUTES = 30;

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final RateLimitGuard rateLimit;
    private final LightMoveProperties properties;

    /**
     * Its own transaction, never the caller's: after a reconnect commits, the finished one is still bound to
     * the thread, and joining it writes nothing.
     */
    BookingPages(MailboxGateway gateway, MailboxConnectionRepository mailboxes, UserRepository users,
                 PlatformTransactionManager transactionManager, RateLimitGuard rateLimit,
                 LightMoveProperties properties) {
        this.gateway = gateway;
        this.mailboxes = mailboxes;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rateLimit = rateLimit;
        this.properties = properties;
    }

    public boolean isOffered() {
        return gateway.isOffered() && gateway.isBookingPageOffered();
    }

    /** The link, or null while the consultant has none yet. */
    public String linkOf(MailboxConnection mailbox) {
        return mailbox.getBookingSlug() == null ? null : properties.web().baseUrl() + "/book/" + mailbox.getBookingSlug();
    }

    /** The link, giving the consultant a slug first if they have none. Written in the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String claimLink(MailboxConnection mailbox) {
        return claimLinkOf(mailbox, nameOf(mailbox));
    }

    /**
     * Before a sequence using {@code {{bookingLink}}} starts: the sender's link and the page behind it both
     * exist, so a failure at the mail service is the consultant's to see, never an executive's. A sender
     * without a sending mailbox is left to Start's own refusal.
     */
    public void prepare(UUID userId, UUID workspaceId) {
        if (!isOffered()) {
            throw ApiException.of(ErrorCode.OUTREACH_BOOKING_LINK_UNAVAILABLE);
        }
        String consultantName = nameOf(userId);
        MailboxConnection mailbox;
        try {
            mailbox = claimedLinkOf(userId, workspaceId, consultantName);
        } catch (DataIntegrityViolationException raced) {
            // Two first Starts drew the same slug in the same instant; the second draws again.
            mailbox = claimedLinkOf(userId, workspaceId, consultantName);
        }
        if (mailbox != null && mailbox.getBookingConfigurationId() == null) {
            createPage(mailbox, consultantName);
        }
    }

    /**
     * A reconnect brings a new grant, and the old page went with the old one. Made again here, off any
     * request, so the public page never has to call the mail service.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onConnected(MailboxConnected connected) {
        if (!isOffered()) {
            return;
        }
        mailboxes.findById(connected.mailboxConnectionId())
                .filter(MailboxConnection::canSend)
                .filter(mailbox -> mailbox.getBookingSlug() != null && mailbox.getBookingConfigurationId() == null)
                .ifPresent(mailbox -> {
                    try {
                        createPage(mailbox, nameOf(mailbox));
                    } catch (ApiException failed) {
                        log.warn("Booking page for mailbox {} was not made again after a reconnect; the next "
                                + "Start makes it", mailbox.getId());
                    }
                });
    }

    /**
     * The public page's read: no write and no call to the mail service. Any link that leads nowhere answers
     * alike, so a slug says nothing it should not.
     */
    @Transactional(readOnly = true)
    public BookingPageResponse open(String slug, HttpServletRequest request) {
        rateLimit.checkBookingPageOpen(slug, request);
        if (!isOffered() || slug == null || !BookingSlug.SHAPE.matcher(slug).matches()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        MailboxConnection mailbox = mailboxes.findByBookingSlug(slug)
                .filter(MailboxConnection::canSend)
                .filter(found -> found.getBookingConfigurationId() != null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return new BookingPageResponse(mailbox.getBookingConfigurationId(), properties.outreach().nylas().baseUrl(),
                nameOf(mailbox));
    }

    private MailboxConnection claimedLinkOf(UUID userId, UUID workspaceId, String consultantName) {
        return transactions.execute(status -> mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(MailboxConnection::canSend)
                .map(found -> {
                    claimLinkOf(found, consultantName);
                    mailboxes.flush();
                    return found;
                })
                .orElse(null));
    }

    private String claimLinkOf(MailboxConnection mailbox, String consultantName) {
        if (mailbox.getBookingSlug() == null) {
            mailbox.claimBookingSlug(BookingSlug.from(consultantName, mailboxes::existsByBookingSlug));
        }
        return linkOf(mailbox);
    }

    private void createPage(MailboxConnection mailbox, String consultantName) {
        String configurationId;
        try {
            configurationId = gateway.createBookingPage(mailbox.getGrantId(), specOf(mailbox, consultantName));
        } catch (VendorException failed) {
            if (MailboxService.isAccessWithdrawn(failed)) {
                transactions.executeWithoutResult(status -> mailboxes.findById(mailbox.getId())
                        .ifPresent(MailboxConnection::markAccessWithdrawn));
                throw ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
            }
            log.warn("Booking page for mailbox {} could not be made at the mail service: {}", mailbox.getId(),
                    failed.getKind());
            throw ApiException.of(ErrorCode.OUTREACH_BOOKING_LINK_FAILED);
        }
        // A reconnect between the call and this write brought a new grant, and the page belongs to the old one.
        transactions.executeWithoutResult(status -> mailboxes.findById(mailbox.getId())
                .filter(fresh -> fresh.getGrantId().equals(mailbox.getGrantId()))
                .ifPresent(fresh -> fresh.holdBookingPage(configurationId)));
    }

    private BookingPageSpec specOf(MailboxConnection mailbox, String consultantName) {
        OutreachSettings settings = properties.outreach();
        String name = consultantName != null ? consultantName : mailbox.getAddress();
        return new BookingPageSpec(mailbox.getAddress(), name, CALL_MINUTES + "-minute call with " + name,
                CALL_MINUTES, mailbox.zone(), settings.windowStart(), settings.windowEnd(),
                Set.copyOf(settings.workingDays()));
    }

    private String nameOf(MailboxConnection mailbox) {
        return nameOf(mailbox.getUserId());
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::getFullName).orElse(null);
    }
}
