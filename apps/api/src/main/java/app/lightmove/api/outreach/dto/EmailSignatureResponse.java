package app.lightmove.api.outreach.dto;

import app.lightmove.api.core.storage.constant.PictureShape;
import app.lightmove.api.outreach.constant.SignatureLayout;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One of the caller's signatures, and how it draws today: {@code html} is what an email would carry. */
public record EmailSignatureResponse(
        UUID id,
        String name,
        SignatureLayout layout,
        SignatureFieldsDto fields,
        boolean showPhoto,
        PictureShape photoShape,
        boolean showLogo,
        boolean showBookingLink,
        String customBody,
        List<FooterPictureResponse> footerPictures,
        String disclaimer,
        boolean isDefault,
        Instant updatedAt,
        String html) {}
