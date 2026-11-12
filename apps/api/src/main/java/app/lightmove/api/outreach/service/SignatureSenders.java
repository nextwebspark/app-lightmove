package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.storage.constant.PhotoShape;
import app.lightmove.api.core.storage.model.EmailImage;
import app.lightmove.api.core.storage.service.EmailImageService;
import app.lightmove.api.outreach.dto.SignatureSourcesResponse;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.SignatureLogo;
import app.lightmove.api.outreach.model.SignaturePicture;
import app.lightmove.api.outreach.model.SignatureSender;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What a consultant's signatures follow: their profile, their workspace, the mailbox they send from and their booking
 * link. The photo and logo are copied into the email-image store the first time a signature draws them, outside any
 * transaction, since the logo is a call to another host.
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

    /** For a preview or the list: nothing is written, and a booking link not yet claimed is drawn as a stand-in. */
    public SignatureSender forPreview(UUID userId, UUID workspaceId, SignatureNeeds needs) {
        return senderOf(userId, workspaceId, needs, false);
    }

    /**
     * For an email about to go: a booking link is claimed and the page behind it made first, so a failure at the mail
     * service is the consultant's to see, never an executive's.
     */
    public SignatureSender forSending(UUID userId, UUID workspaceId, SignatureNeeds needs) {
        if (needs.booking()) {
            boolean offered = transactions.execute(status -> mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .filter(MailboxConnection::canSend).map(bookingPages::isOfferedFor).orElse(false));
            if (offered) {
                bookingPages.prepare(userId, workspaceId);
            }
        }
        return senderOf(userId, workspaceId, needs, true);
    }

    private SignatureSender senderOf(UUID userId, UUID workspaceId, SignatureNeeds needs, boolean sending) {
        Map<PhotoShape, String> photos = new EnumMap<>(PhotoShape.class);
        needs.photoShapes().forEach(shape -> emailImages.photoOf(userId, shape)
                .ifPresent(photo -> photos.put(shape, emailImages.urlOf(photo))));
        SignatureLogo logo = needs.logo() ? logoOf(workspaceId) : null;
        Map<String, SignaturePicture> pictures = picturesOf(userId, workspaceId, needs.pictureTokens());
        return transactions.execute(status -> {
            User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            Workspace workspace = workspaces.findById(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            MailboxConnection mailbox = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId).orElse(null);
            String bookingLink = needs.booking() && mailbox != null && bookingPages.isOfferedFor(mailbox)
                    ? (sending ? bookingPages.linkOf(mailbox) : bookingPages.previewLinkOf(mailbox)) : null;
            return new SignatureSender(user.getFullName(), user.getTitle(), user.getPhone(), user.getLinkedinUrl(),
                    mailbox == null ? user.getEmail() : mailbox.getAddress(), workspace.getName(),
                    workspace.getCompanyWebsite(), photos, logo, bookingLink, pictures);
        });
    }

    public SignatureSourcesResponse sourcesOf(UUID userId, UUID workspaceId) {
        return transactions.execute(status -> {
            User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            Workspace workspace = workspaces.findById(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            MailboxConnection mailbox = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId).orElse(null);
            return new SignatureSourcesResponse(user.getFullName(), user.getTitle(), user.getPhone(),
                    user.getLinkedinUrl(), mailbox == null ? user.getEmail() : mailbox.getAddress(), workspace.getName(),
                    workspace.getCompanyWebsite(), user.getAvatarUrl(),
                    workspace.getLogoUrl() != null && !workspace.getLogoUrl().isBlank(),
                    mailbox != null && bookingPages.isOfferedFor(mailbox));
        });
    }

    /** Only the signer's own pictures in this workspace: a token copied from someone else's signature draws nothing. */
    private Map<String, SignaturePicture> picturesOf(UUID userId, UUID workspaceId, List<String> tokens) {
        return emailImages.signaturePicturesOf(workspaceId, userId, tokens).stream()
                .collect(Collectors.toMap(EmailImage::getToken,
                        image -> new SignaturePicture(emailImages.urlOf(image), image.getWidth(), image.getHeight())));
    }

    private SignatureLogo logoOf(UUID workspaceId) {
        String logoUrl = workspaces.findById(workspaceId).map(Workspace::getLogoUrl).orElse(null);
        return emailImages.logoOf(workspaceId, logoUrl)
                .map(image -> new SignatureLogo(emailImages.urlOf(image), image.getWidth(), image.getHeight()))
                .orElse(null);
    }
}
