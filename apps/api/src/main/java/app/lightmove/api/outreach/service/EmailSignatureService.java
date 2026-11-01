package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.outreach.constant.SignatureLayout;
import app.lightmove.api.outreach.dto.EmailSignatureResponse;
import app.lightmove.api.outreach.dto.EmailSignaturesResponse;
import app.lightmove.api.outreach.dto.RenderedSignatureResponse;
import app.lightmove.api.outreach.dto.SaveSignatureRequest;
import app.lightmove.api.outreach.dto.SignatureFieldsDto;
import app.lightmove.api.outreach.model.EmailSignature;
import app.lightmove.api.outreach.model.RenderedSignature;
import app.lightmove.api.outreach.model.SignatureContent;
import app.lightmove.api.outreach.model.SignatureFields;
import app.lightmove.api.outreach.model.SignatureSender;
import app.lightmove.api.outreach.repository.EmailSignatureRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A consultant's email signatures in one workspace: theirs alone, so every read and write is keyed on the caller and the
 * workspace from the principal, and another person's signature reads as one that does not exist. At most one is the
 * default, the one the composer ticks.
 */
@Service
@RequiredArgsConstructor
public class EmailSignatureService {

    public static final int MAX_SIGNATURES = 10;

    private final EmailSignatureRepository signatures;
    private final SignatureSenders senders;
    private final TransactionTemplate transactions;

    public EmailSignaturesResponse list(UUID userId, UUID workspaceId) {
        List<EmailSignature> owned = signatures.findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(workspaceId, userId);
        boolean anyLogo = owned.stream().anyMatch(signature -> showsLogo(signature.content()));
        boolean anyBooking = owned.stream().anyMatch(signature -> signature.content().showBookingLink());
        SignatureSender sender = senders.senderOf(userId, workspaceId, anyLogo, anyBooking);
        return new EmailSignaturesResponse(owned.stream().map(signature -> responseOf(signature, sender)).toList(),
                senders.sourcesOf(userId, workspaceId), MAX_SIGNATURES);
    }

    public EmailSignatureResponse create(UUID userId, UUID workspaceId, SaveSignatureRequest request) {
        SignatureContent content = contentOf(request);
        EmailSignature saved = transactions.execute(status -> {
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
        return responseOf(saved, senderFor(userId, workspaceId, content));
    }

    public EmailSignatureResponse update(UUID userId, UUID workspaceId, UUID signatureId, SaveSignatureRequest request) {
        SignatureContent content = contentOf(request);
        EmailSignature saved = transactions.execute(status -> {
            EmailSignature signature = owned(userId, workspaceId, signatureId);
            if (request.makesDefault() && !signature.isDefault()) {
                signatures.clearDefault(workspaceId, userId);
                signature = owned(userId, workspaceId, signatureId);
            }
            signature.write(content);
            signature.markDefault(request.makesDefault());
            return signatures.saveAndFlush(signature);
        });
        return responseOf(saved, senderFor(userId, workspaceId, content));
    }

    public EmailSignatureResponse makeDefault(UUID userId, UUID workspaceId, UUID signatureId) {
        EmailSignature saved = transactions.execute(status -> {
            owned(userId, workspaceId, signatureId);
            signatures.clearDefault(workspaceId, userId);
            EmailSignature signature = owned(userId, workspaceId, signatureId);
            signature.markDefault(true);
            return signatures.saveAndFlush(signature);
        });
        return responseOf(saved, senderFor(userId, workspaceId, saved.content()));
    }

    /** Deleting the default hands the mark to the oldest left, so the composer still ticks one. */
    public void delete(UUID userId, UUID workspaceId, UUID signatureId) {
        transactions.executeWithoutResult(status -> {
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
        RenderedSignature rendered = EmailSignatureRenderer.render(content, senderFor(userId, workspaceId, content));
        return new RenderedSignatureResponse(rendered.html(), rendered.text());
    }

    /** The caller's signature as an email would carry it now; empty for one that is not theirs. */
    public Optional<RenderedSignature> renderFor(UUID userId, UUID workspaceId, UUID signatureId) {
        return signatures.findByIdAndWorkspaceIdAndUserId(signatureId, workspaceId, userId)
                .map(signature -> EmailSignatureRenderer.render(signature.content(),
                        senderFor(userId, workspaceId, signature.content())));
    }

    /** The caller's default signature as an email would carry it now; empty where they keep none. */
    public Optional<RenderedSignature> renderDefaultFor(UUID userId, UUID workspaceId) {
        return signatures.findByWorkspaceIdAndUserIdAndIsDefaultTrue(workspaceId, userId)
                .map(signature -> EmailSignatureRenderer.render(signature.content(),
                        senderFor(userId, workspaceId, signature.content())));
    }

    private EmailSignature owned(UUID userId, UUID workspaceId, UUID signatureId) {
        return signatures.findByIdAndWorkspaceIdAndUserId(signatureId, workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_SIGNATURE_NOT_FOUND));
    }

    private SignatureSender senderFor(UUID userId, UUID workspaceId, SignatureContent content) {
        return senders.senderOf(userId, workspaceId, showsLogo(content), content.showBookingLink());
    }

    private static boolean showsLogo(SignatureContent content) {
        return content.layout().drawsLogo() && content.showLogo();
    }

    private static EmailSignatureResponse responseOf(EmailSignature signature, SignatureSender sender) {
        SignatureContent content = signature.content();
        RenderedSignature rendered = EmailSignatureRenderer.render(content, sender);
        SignatureFields fields = content.fields();
        return new EmailSignatureResponse(signature.getId(), content.name(), content.layout(),
                new SignatureFieldsDto(fields.name(), fields.title(), fields.phone(), fields.linkedinUrl(),
                        fields.company(), fields.website(), fields.address()),
                content.showPhoto(), content.showLogo(), content.showBookingLink(), content.customBody(),
                content.disclaimer(), signature.isDefault(), signature.getUpdatedAt(), rendered.html(), rendered.text());
    }

    /** Trimmed, blank as absent, and an address that is not what its field says refused by name. */
    static SignatureContent contentOf(SaveSignatureRequest request) {
        SignatureFieldsDto typed = request.fields();
        SignatureFields fields = typed == null ? SignatureFields.NONE : new SignatureFields(
                trimmed(typed.name()), trimmed(typed.title()), trimmed(typed.phone()),
                linkedinOrNull(typed.linkedinUrl()), trimmed(typed.company()), websiteOrNull(typed.website()),
                trimmed(typed.address()));
        String customBody = request.layout() == SignatureLayout.CUSTOM ? trimmed(request.customBody()) : null;
        return new SignatureContent(request.name().strip(), request.layout(), fields,
                Boolean.TRUE.equals(request.showPhoto()), Boolean.TRUE.equals(request.showLogo()),
                Boolean.TRUE.equals(request.showBookingLink()), customBody, trimmed(request.disclaimer()));
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
