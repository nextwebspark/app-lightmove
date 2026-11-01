package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.storage.service.EmailImageService;
import app.lightmove.api.outreach.dto.SignatureSourcesResponse;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.SignatureLogo;
import app.lightmove.api.outreach.model.SignatureSender;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What a consultant's signatures follow: their profile, their workspace, the mailbox they send from and their booking
 * link. The logo is copied in from where the workspace's is published the first time it is asked for, outside any
 * transaction, since that is a call to another host.
 */
@Service
@RequiredArgsConstructor
public class SignatureSenders {

    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final MailboxConnectionRepository mailboxes;
    private final BookingPages bookingPages;
    private final EmailImageService emailImages;
    private final TransactionTemplate transactions;

    /**
     * @param withLogo     whether a signature drawn now shows the logo; only then is it fetched
     * @param withBooking  whether it shows the booking link; only then is the consultant given a slug, if they have none
     */
    public SignatureSender senderOf(UUID userId, UUID workspaceId, boolean withLogo, boolean withBooking) {
        SignatureLogo logo = withLogo ? logoOf(workspaceId) : null;
        return transactions.execute(status -> {
            User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            Workspace workspace = workspaces.findById(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            MailboxConnection mailbox = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId).orElse(null);
            String bookingLink = withBooking && mailbox != null && bookingPages.isOfferedFor(mailbox)
                    ? bookingPages.claimLink(mailbox) : null;
            return new SignatureSender(user.getFullName(), user.getTitle(), user.getPhone(), user.getLinkedinUrl(),
                    mailbox == null ? user.getEmail() : mailbox.getAddress(), workspace.getName(),
                    workspace.getCompanyWebsite(), user.hasUploadedPhoto() ? user.getAvatarUrl() : null, logo,
                    bookingLink);
        });
    }

    public SignatureSourcesResponse sourcesOf(UUID userId, UUID workspaceId) {
        return transactions.execute(status -> {
            User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            Workspace workspace = workspaces.findById(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            MailboxConnection mailbox = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId).orElse(null);
            return new SignatureSourcesResponse(user.getFullName(), user.getTitle(), user.getPhone(),
                    user.getLinkedinUrl(), mailbox == null ? user.getEmail() : mailbox.getAddress(), workspace.getName(),
                    workspace.getCompanyWebsite(), user.hasUploadedPhoto() ? user.getAvatarUrl() : null,
                    workspace.getLogoUrl() != null && !workspace.getLogoUrl().isBlank(),
                    mailbox != null && bookingPages.isOfferedFor(mailbox));
        });
    }

    private SignatureLogo logoOf(UUID workspaceId) {
        String logoUrl = workspaces.findById(workspaceId).map(Workspace::getLogoUrl).orElse(null);
        return emailImages.logoOf(workspaceId, logoUrl)
                .map(image -> new SignatureLogo(emailImages.urlOf(image), image.getWidth(), image.getHeight()))
                .orElse(null);
    }
}
