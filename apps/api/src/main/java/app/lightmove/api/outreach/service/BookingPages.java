package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.dto.BookingPageResponse;
import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.BookingSlug;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A consultant's booking link: {@code <web.base-url>/book/<slug>}, a public page where an executive picks a
 * time on their calendar. The slug is all an email needs, so a send never waits on the mail service; the
 * Scheduler page behind it is made at Start, and again by the page itself after a reconnect.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingPages {

    static final int CALL_MINUTES = 30;

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;

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
        return claimLinkOf(mailbox);
    }

    /**
     * Before a sequence using {@code {{bookingLink}}} starts: the sender's link and the page behind it both
     * exist, so a failure at the mail service is the consultant's to see, never an executive's.
     */
    public void prepare(UUID userId, UUID workspaceId) {
        if (!isOffered()) {
            throw ApiException.of(ErrorCode.OUTREACH_BOOKING_LINK_UNAVAILABLE);
        }
        MailboxConnection mailbox = transactions.execute(status -> {
            MailboxConnection found = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_NOT_CONNECTED));
            if (!found.canSend()) {
                throw ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
            }
            claimLinkOf(found);
            return found;
        });
        if (mailbox.getBookingConfigurationId() == null) {
            createPage(mailbox);
        }
    }

    /** The public page's read. Any link that leads nowhere answers alike, so a slug says nothing it should not. */
    public BookingPageResponse open(String slug) {
        if (!isOffered() || slug == null || !BookingSlug.SHAPE.matcher(slug).matches()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        MailboxConnection mailbox = mailboxes.findByBookingSlug(slug)
                .filter(MailboxConnection::canSend)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        String configurationId = mailbox.getBookingConfigurationId() != null
                ? mailbox.getBookingConfigurationId()
                : createPage(mailbox);
        String name = users.findById(mailbox.getUserId()).map(User::getFullName).orElse(null);
        return new BookingPageResponse(configurationId, properties.outreach().nylas().baseUrl(), name);
    }

    private String claimLinkOf(MailboxConnection mailbox) {
        if (mailbox.getBookingSlug() == null) {
            String name = users.findById(mailbox.getUserId()).map(User::getFullName).orElse(null);
            mailbox.claimBookingSlug(BookingSlug.from(name, mailboxes::existsByBookingSlug));
        }
        return linkOf(mailbox);
    }

    private String createPage(MailboxConnection mailbox) {
        String configurationId;
        try {
            configurationId = gateway.createBookingPage(mailbox.getGrantId(), specOf(mailbox));
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
        return configurationId;
    }

    private BookingPageSpec specOf(MailboxConnection mailbox) {
        OutreachSettings settings = properties.outreach();
        String name = users.findById(mailbox.getUserId()).map(User::getFullName).orElse(mailbox.getAddress());
        return new BookingPageSpec(mailbox.getAddress(), name, CALL_MINUTES + "-minute call with " + name,
                CALL_MINUTES, mailbox.zone(), settings.windowStart(), settings.windowEnd(),
                Set.copyOf(settings.workingDays()));
    }
}
