package app.lightmove.api.outreach.dto;

import app.lightmove.api.core.storage.constant.PhotoShape;
import app.lightmove.api.outreach.constant.SignatureLayout;
import app.lightmove.api.outreach.model.SignaturePicture;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A signature as Settings → Email signature saves or previews it. {@code customBody} is the whole of a
 * {@link SignatureLayout#CUSTOM} signature and the extra words under any other layout's details; {@code footerPictures}
 * are uploaded pictures' tokens; {@code isDefault} makes it the one the composer ticks, the previous default giving way.
 * A switch left out is off; a shape left out is a circle.
 */
public record SaveSignatureRequest(
        @NotBlank(message = "Name this signature") @Size(max = 60, message = "That name is too long") String name,
        @NotNull(message = "Pick a layout") SignatureLayout layout,
        @Valid SignatureFieldsDto fields,
        Boolean showPhoto,
        PhotoShape photoShape,
        Boolean showLogo,
        Boolean showBookingLink,
        @Size(max = 2000, message = "That signature is too long") String customBody,
        @Size(max = SignaturePicture.MAX_PER_SIGNATURE, message = "Keep it to 6 pictures") List<String> footerPictures,
        @Size(max = 1000, message = "That disclaimer is too long") String disclaimer,
        Boolean isDefault) {

    /** A switch left out is off. */
    public boolean makesDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
