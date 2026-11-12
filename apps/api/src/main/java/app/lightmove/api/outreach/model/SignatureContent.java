package app.lightmove.api.outreach.model;

import app.lightmove.api.core.storage.constant.PictureShape;
import app.lightmove.api.outreach.constant.SignatureLayout;

/**
 * Everything a signature says, checked and trimmed: what is saved, and what a preview draws without saving.
 * {@code customBody} is the whole signature for {@link SignatureLayout#CUSTOM}, and for any other layout the extra words
 * drawn under the details.
 */
public record SignatureContent(String name, SignatureLayout layout, SignatureFields fields, boolean showPhoto,
                               PictureShape photoShape, boolean showLogo, boolean showBookingLink, String customBody,
                               String disclaimer) {

    public SignatureContent {
        photoShape = photoShape == null ? PictureShape.CIRCLE : photoShape;
    }

    public SignatureContent withoutBookingLink() {
        return new SignatureContent(name, layout, fields, showPhoto, photoShape, showLogo, false, customBody,
                disclaimer);
    }
}
