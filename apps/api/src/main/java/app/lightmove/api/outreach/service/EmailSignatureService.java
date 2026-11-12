package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.storage.constant.EmailImageKind;
import app.lightmove.api.core.storage.model.EmailImage;
import app.lightmove.api.core.storage.service.EmailImageService;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.text.service.PhoneNumbers;
import app.lightmove.api.outreach.dto.EmailSignatureResponse;
import app.lightmove.api.outreach.dto.EmailSignaturesResponse;
import app.lightmove.api.outreach.dto.RenderedSignatureResponse;
import app.lightmove.api.outreach.dto.SaveSignatureRequest;
import app.lightmove.api.outreach.dto.SignatureFieldsDto;
import app.lightmove.api.outreach.dto.SignaturePictureResponse;
import app.lightmove.api.outreach.dto.SignatureStarterResponse;
import app.lightmove.api.outreach.model.EmailSignature;
import app.lightmove.api.outreach.model.OutreachMarkdownEmail;
import app.lightmove.api.outreach.model.RenderedSignature;
import app.lightmove.api.outreach.model.SignatureContent;
import app.lightmove.api.outreach.model.SignatureFields;
import app.lightmove.api.outreach.model.SignaturePicture;
import app.lightmove.api.outreach.model.SignatureSender;
import app.lightmove.api.outreach.repository.EmailSignatureRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A consultant's email signatures in one workspace: theirs alone, so every read and write is keyed on the caller and the
 * workspace from the principal, and another person's signature reads as one that does not exist. While any exist,
 * exactly one is the default, the one the composer ticks.
 */
@Service
@RequiredArgsConstructor
public class EmailSignatureService {

    public static final int MAX_SIGNATURES = 10;

    private final EmailSignatureRepository signatures;
    private final SignatureSenders senders;
    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final EmailImageService emailImages;

    public EmailSignaturesResponse list(UUID userId, UUID workspaceId) {
        List<EmailSignature> owned = signatures.findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(workspaceId, userId);
        SignatureSender sender = senders.forPreview(userId, workspaceId,
                SignatureNeeds.ofAll(owned.stream().map(EmailSignature::content).toList()));
        return new EmailSignaturesResponse(owned.stream().map(signature -> responseOf(signature, sender)).toList(),
                senders.sourcesOf(userId, workspaceId), MAX_SIGNATURES);
    }

    public EmailSignatureResponse create(UUID userId, UUID workspaceId, SaveSignatureRequest request) {
        SignatureContent content = contentOf(request);
        requireOwnPictures(userId, workspaceId, content);
        EmailSignature saved = transactions.execute(status -> {
            lockSignaturesOf(userId);
            if (signatures.countByWorkspaceIdAndUserId(workspaceId, userId) >= MAX_SIGNATURES) {
                throw ApiException.of(ErrorCode.EMAIL_SIGNATURE_LIMIT_REACHED);
            }
            boolean firstOne = signatures.findByWorkspaceIdAndUserIdAndIsDefaultTrue(workspaceId, userId).isEmpty();
            if (request.makesDefault() && !firstOne) {
                signatures.clearDefault(workspaceId, userId);
            }
            EmailSignature signature = EmailSignature.of(workspaceId, userId, content);
            signature.markDefault(request.makesDefault() || firstOne);
            return signatures.saveAndFlush(signature);
        });
        return responseOf(saved, previewSender(userId, workspaceId, content));
    }

    /** The default stays the default until another is made one: unticking it alone would leave the composer none. */
    public EmailSignatureResponse update(UUID userId, UUID workspaceId, UUID signatureId, SaveSignatureRequest request) {
        SignatureContent content = contentOf(request);
        requireOwnPictures(userId, workspaceId, content);
        EmailSignature saved = transactions.execute(status -> {
            lockSignaturesOf(userId);
            EmailSignature signature = owned(userId, workspaceId, signatureId);
            if (request.makesDefault() && !signature.isDefault()) {
                signatures.clearDefault(workspaceId, userId);
                signature = owned(userId, workspaceId, signatureId);
            }
            signature.write(content);
            signature.markDefault(request.makesDefault() || signature.isDefault());
            return signatures.saveAndFlush(signature);
        });
        return responseOf(saved, previewSender(userId, workspaceId, content));
    }

    public EmailSignatureResponse makeDefault(UUID userId, UUID workspaceId, UUID signatureId) {
        EmailSignature saved = transactions.execute(status -> {
            lockSignaturesOf(userId);
            owned(userId, workspaceId, signatureId);
            signatures.clearDefault(workspaceId, userId);
            EmailSignature signature = owned(userId, workspaceId, signatureId);
            signature.markDefault(true);
            return signatures.saveAndFlush(signature);
        });
        return responseOf(saved, previewSender(userId, workspaceId, saved.content()));
    }

    /** Deleting the default hands the mark to the oldest left, so the composer still ticks one. */
    public void delete(UUID userId, UUID workspaceId, UUID signatureId) {
        transactions.executeWithoutResult(status -> {
            lockSignaturesOf(userId);
            EmailSignature signature = owned(userId, workspaceId, signatureId);
            boolean wasDefault = signature.isDefault();
            signatures.delete(signature);
            signatures.flush();
            if (wasDefault) {
                signatures.findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(workspaceId, userId).stream().findFirst()
                        .ifPresent(next -> next.markDefault(true));
            }
        });
    }

    public RenderedSignatureResponse preview(UUID userId, UUID workspaceId, SaveSignatureRequest request) {
        SignatureContent content = contentOf(request);
        RenderedSignature rendered = EmailSignatureRenderer.render(content, previewSender(userId, workspaceId, content));
        return new RenderedSignatureResponse(rendered.html());
    }

    /**
     * A picture for the caller's Custom signature, kept and served by Uncava like the photo and logo. The editor writes
     * the returned address into the signature's Markdown; a save then holds it to being the caller's own.
     */
    public SignaturePictureResponse uploadPicture(UUID userId, UUID workspaceId, byte[] content) {
        EmailImage picture = emailImages.uploadSignaturePicture(workspaceId, userId, content)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_IMAGE_UNREADABLE));
        return new SignaturePictureResponse(picture.getToken(), EmailImageService.PATH + picture.getToken(),
                picture.getWidth(), picture.getHeight());
    }

    /**
     * {@code request} as a Custom signature's Markdown, for the editor to go on with: a starting point filled from the
     * profile, workspace and mailbox, or an older layout opened for editing. Writes nothing but the photo's and logo's
     * copies, which a preview makes too.
     */
    public SignatureStarterResponse starterOf(UUID userId, UUID workspaceId, SaveSignatureRequest request) {
        SignatureContent content = contentOf(request);
        SignatureNeeds needs = SignatureNeeds.of(content);
        SignatureSender sender = senders.forPreview(userId, workspaceId,
                new SignatureNeeds(needs.photoShapes(), needs.logo(), false, needs.pictureTokens()));
        return new SignatureStarterResponse(EmailSignatureRenderer.asMarkdown(content, sender));
    }

    /**
     * The signature being edited, saved or not, drawn for the caller's own test email. As with the default's test, a
     * booking page that cannot be made leaves the Book a call line out rather than failing it.
     */
    public RenderedSignature renderDraftForTest(UUID userId, UUID workspaceId, SaveSignatureRequest draft) {
        return renderedForTest(userId, workspaceId, contentOf(draft));
    }

    /**
     * The HTML of the signature the caller chose for an email, or a sequence run, about to go — its booking page made
     * first where it shows the link. Empty where none was chosen; {@code EMAIL_SIGNATURE_NOT_FOUND} for one that is not
     * theirs, in this workspace.
     */
    public Optional<String> htmlForSending(UUID userId, UUID workspaceId, UUID signatureId) {
        return Optional.ofNullable(signatureId)
                .map(chosen -> renderedForSending(userId, workspaceId, owned(userId, workspaceId, chosen)).html());
    }

    /**
     * The default for the mailbox's test email. The test proves the mailbox sends, so a booking page that cannot be made
     * leaves the Book a call line out rather than failing it; a mailbox the page found withdrawn still fails it.
     */
    public Optional<RenderedSignature> renderDefaultForTest(UUID userId, UUID workspaceId) {
        return signatures.findByWorkspaceIdAndUserIdAndIsDefaultTrue(workspaceId, userId)
                .map(signature -> renderedForTest(userId, workspaceId, signature.content()));
    }

    private RenderedSignature renderedForTest(UUID userId, UUID workspaceId, SignatureContent content) {
        try {
            return EmailSignatureRenderer.render(content, senders.forSending(userId, workspaceId, SignatureNeeds.of(content)));
        } catch (ApiException bookingPage) {
            if (bookingPage.getCode() != ErrorCode.OUTREACH_BOOKING_LINK_FAILED
                    && bookingPage.getCode() != ErrorCode.OUTREACH_BOOKING_LINK_UNAVAILABLE) {
                throw bookingPage;
            }
            SignatureContent unbooked = content.withoutBookingLink();
            return EmailSignatureRenderer.render(unbooked,
                    senders.forSending(userId, workspaceId, SignatureNeeds.of(unbooked)));
        }
    }

    /**
     * Every picture a saved signature names, in its words or its footer, is the caller's to show — an upload of theirs
     * here, their photo, the workspace's logo — and there are at most six in all. A token copied out of a colleague's
     * signature would draw nothing in an email, so it is refused here instead.
     */
    private void requireOwnPictures(UUID userId, UUID workspaceId, SignatureContent content) {
        int inWords = OutreachMarkdownEmail.pictureCountIn(content.customBody());
        if (inWords + content.footerPictures().size() > SignaturePicture.MAX_PER_SIGNATURE) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED,
                    content.footerPictures().isEmpty() ? "customBody" : "footerPictures", "Keep it to 6 pictures");
        }
        List<String> inBody = OutreachMarkdownEmail.pictureTokensIn(content.customBody());
        if (emailImages.signaturePicturesOf(workspaceId, userId, inBody).size() != inBody.size()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "customBody",
                    "A picture in it could not be found. Remove it and insert it again");
        }
        List<String> uploaded = emailImages.signaturePicturesOf(workspaceId, userId, content.footerPictures()).stream()
                .filter(image -> image.getKind() == EmailImageKind.SIGNATURE).map(EmailImage::getToken).toList();
        if (!uploaded.containsAll(content.footerPictures())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "footerPictures",
                    "A picture could not be found. Remove it and add it again");
        }
    }

    private RenderedSignature renderedForSending(UUID userId, UUID workspaceId, EmailSignature signature) {
        return EmailSignatureRenderer.render(signature.content(),
                senders.forSending(userId, workspaceId, SignatureNeeds.of(signature.content())));
    }

    /**
     * Every write that moves the default takes this first. Without it, two Make defaults in flight together each clear
     * only the default their snapshot saw, and the second meets the first's new one on the one-default index.
     */
    private void lockSignaturesOf(UUID userId) {
        users.lockForOwnContentChange(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private EmailSignature owned(UUID userId, UUID workspaceId, UUID signatureId) {
        return signatures.findByIdAndWorkspaceIdAndUserId(signatureId, workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_SIGNATURE_NOT_FOUND));
    }

    private SignatureSender previewSender(UUID userId, UUID workspaceId, SignatureContent content) {
        return senders.forPreview(userId, workspaceId, SignatureNeeds.of(content));
    }

    private static EmailSignatureResponse responseOf(EmailSignature signature, SignatureSender sender) {
        SignatureContent content = signature.content();
        RenderedSignature rendered = EmailSignatureRenderer.render(content, sender);
        SignatureFields fields = content.fields();
        List<SignaturePictureResponse> footer = content.footerPictures().stream()
                .filter(token -> sender.pictures().containsKey(token))
                .map(token -> new SignaturePictureResponse(token, EmailImageService.PATH + token,
                        sender.pictures().get(token).width(), sender.pictures().get(token).height()))
                .toList();
        return new EmailSignatureResponse(signature.getId(), content.name(), content.layout(),
                new SignatureFieldsDto(fields.name(), fields.title(), fields.phone(), fields.linkedinUrl(),
                        fields.company(), fields.website(), fields.address(), fields.mobile()),
                content.showPhoto(), content.photoShape(), content.showLogo(), content.showBookingLink(),
                content.customBody(), footer, content.disclaimer(), signature.isDefault(), signature.getUpdatedAt(),
                rendered.html());
    }

    /** Trimmed, blank as absent, and an address that is not what its field says refused by name. */
    static SignatureContent contentOf(SaveSignatureRequest request) {
        SignatureFieldsDto typed = request.fields();
        SignatureFields fields = typed == null ? SignatureFields.NONE : new SignatureFields(
                trimmed(typed.name()), trimmed(typed.title()), PhoneNumbers.typedOrNull(typed.phone(), "fields.phone"),
                linkedinOrNull(typed.linkedinUrl()), trimmed(typed.company()), websiteOrNull(typed.website()),
                trimmed(typed.address()), PhoneNumbers.typedOrNull(typed.mobile(), "fields.mobile"));
        List<String> footer = request.footerPictures() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.footerPictures().stream()
                        .filter(token -> token != null && !token.isBlank()).map(String::strip).toList()));
        return new SignatureContent(request.name().strip(), request.layout(), fields,
                Boolean.TRUE.equals(request.showPhoto()), request.photoShape(), Boolean.TRUE.equals(request.showLogo()),
                Boolean.TRUE.equals(request.showBookingLink()), trimmed(request.customBody()), footer,
                trimmed(request.disclaimer()));
    }

    private static String linkedinOrNull(String value) {
        String typed = trimmed(value);
        if (typed == null) {
            return null;
        }
        String href = EmailSignatureRenderer.websiteHref(typed);
        if (href == null || LinkedInUrls.profileSlugOrNull(href) == null && LinkedInUrls.companySlugOrNull(href) == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "fields.linkedinUrl",
                    "Enter a LinkedIn address, like linkedin.com/in/your-name");
        }
        return href;
    }

    private static String websiteOrNull(String value) {
        String typed = trimmed(value);
        if (typed != null && EmailSignatureRenderer.websiteHref(typed) == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "fields.website",
                    "Enter a website address, like yourcompany.com");
        }
        return typed;
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
