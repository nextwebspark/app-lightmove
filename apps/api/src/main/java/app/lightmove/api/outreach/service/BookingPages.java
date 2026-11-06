package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.tenant.ResolvedRow;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.outreach.constant.BookingPageKind;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.dto.BookingPageResponse;
import app.lightmove.api.outreach.model.AvailabilityChanged;
import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.BookingSlug;
import app.lightmove.api.outreach.model.MailboxConnected;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
    private final AvailabilityService availability;
    private final TenantResolvers resolvers;

    /**
     * Its own transaction, never the caller's: after a reconnect commits, the finished one is still bound to
     * the thread, and joining it writes nothing.
     */
    BookingPages(MailboxGateway gateway, MailboxConnectionRepository mailboxes, UserRepository users,
                 PlatformTransactionManager transactionManager, RateLimitGuard rateLimit,
                 LightMoveProperties properties, AvailabilityService availability, TenantResolvers resolvers) {
        this.gateway = gateway;
        this.mailboxes = mailboxes;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rateLimit = rateLimit;
        this.properties = properties;
        this.availability = availability;
        this.resolvers = resolvers;
    }

    /** A direct mailbox's page is always Uncava's own; a Nylas one needs the plan to carry Scheduler. */
    public boolean isOffered() {
        return gateway.isOffered() && (gateway.isBookingPageOffered() || gateway.ownBookingPagesOffered());
    }

    /** Whether this consultant's link can be sent from this mailbox. */
    public boolean isOfferedFor(MailboxConnection mailbox) {
        return gateway.isOffered() && (mailbox.isDirect() || gateway.isBookingPageOffered());
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
        if (mailbox == null || mailbox.isDirect()) {
            return;
        }
        if (!gateway.isBookingPageOffered()) {
            throw ApiException.of(ErrorCode.OUTREACH_BOOKING_LINK_UNAVAILABLE);
        }
        if (mailbox.getBookingConfigurationId() == null) {
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
        if (!gateway.isBookingPageOffered()) {
            return;
        }
        mailboxes.findById(connected.mailboxConnectionId())
                .filter(MailboxConnection::canSend)
                .filter(mailbox -> !mailbox.isDirect())
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
     * New working hours reach every Nylas page they shape: the consultant's own, or each of the workspace's for a
     * change to its default. Off any request; a page that cannot be updated keeps its old hours until it is made again
     * at a reconnect, and the consultant's own page (Uncava's) never needs this.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAvailabilityChanged(AvailabilityChanged changed) {
        if (!gateway.isBookingPageOffered()) {
            return;
        }
        List<MailboxConnection> affected = changed.userId() == null
                ? mailboxes.findByWorkspaceIdAndGateway(changed.workspaceId(), MailboxGatewayKind.NYLAS)
                : mailboxes.findByWorkspaceIdAndUserId(changed.workspaceId(), changed.userId()).stream().toList();
        affected.stream()
                .filter(MailboxConnection::canSend)
                .filter(mailbox -> !mailbox.isDirect() && mailbox.getBookingConfigurationId() != null)
                .forEach(this::updatePage);
    }

    private void updatePage(MailboxConnection mailbox) {
        try {
            gateway.updateBookingPage(mailbox.getGrantId(), mailbox.getBookingConfigurationId(),
                    specOf(mailbox, nameOf(mailbox)));
        } catch (VendorException failed) {
            log.warn("Booking page {} of mailbox {} kept its old hours: the mail service refused the update ({})",
                    mailbox.getBookingConfigurationId(), mailbox.getId(), failed.getKind());
        }
    }

    /**
     * The public page's read: no write and no call to the mail service. Any link that leads nowhere answers
     * alike, so a slug says nothing it should not.
     */
    public BookingPageResponse open(String slug, HttpServletRequest request) {
        rateLimit.checkBookingPageOpen(slug, request);
        ResolvedRow page = pageNamed(slug);
        return WorkspaceScope.call(page.workspaceId(), () -> transactions.execute(status -> openInScope(page)));
    }

    /**
     * Nobody is signed in: the slug is the link's whole identity, and names the workspace its page is read in. A slug
     * of no shape, or one nobody holds, answers as every link that leads nowhere does.
     */
    ResolvedRow pageNamed(String slug) {
        if (!gateway.isOffered() || slug == null || !BookingSlug.SHAPE.matcher(slug).matches()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return resolvers.mailboxByBookingSlug(slug).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private BookingPageResponse openInScope(ResolvedRow page) {
        MailboxConnection mailbox = mailboxes.findById(page.id())
                .filter(MailboxConnection::canSend)
                .filter(found -> found.isDirect()
                        || (gateway.isBookingPageOffered() && found.getBookingConfigurationId() != null))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (mailbox.isDirect()) {
            return new BookingPageResponse(BookingPageKind.DIRECT, null, null, nameOf(mailbox), CALL_MINUTES);
        }
        return new BookingPageResponse(BookingPageKind.NYLAS, mailbox.getBookingConfigurationId(),
                properties.outreach().nylas().baseUrl(), nameOf(mailbox), CALL_MINUTES);
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
            // Unique across every workspace, since the link names nothing else: asked of all of them, not this one.
            mailbox.claimBookingSlug(BookingSlug.from(consultantName,
                    taken -> resolvers.mailboxByBookingSlug(taken).isPresent()));
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

    /** A Nylas calendar states no working hours of its own, so the page keeps the consultant's or the workspace's. */
    private BookingPageSpec specOf(MailboxConnection mailbox, String consultantName) {
        String name = consultantName != null ? consultantName : mailbox.getAddress();
        return new BookingPageSpec(mailbox.getAddress(), name, CALL_MINUTES + "-minute call with " + name,
                CALL_MINUTES, mailbox.zone(),
                availability.workingHoursOf(mailbox.getWorkspaceId(), mailbox.getUserId(), mailbox.zone()).week());
    }

    String nameOf(MailboxConnection mailbox) {
        return nameOf(mailbox.getUserId());
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::getFullName).orElse(null);
    }
}
