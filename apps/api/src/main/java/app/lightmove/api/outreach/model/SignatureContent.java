package app.lightmove.api.outreach.model;

import app.lightmove.api.core.storage.constant.PhotoShape;
import app.lightmove.api.outreach.constant.SignatureLayout;
import java.util.List;

/**
 * Everything a signature says, checked and trimmed: what is saved, and what a preview draws without saving.
 * {@code customBody} is the whole signature for {@link SignatureLayout#CUSTOM}, and for any other layout the extra words
 * drawn under the details. {@code footerPictures} are tokens of the signer's own pictures, drawn in a row at the foot.
 */
public record SignatureContent(String name, SignatureLayout layout, SignatureFields fields, boolean showPhoto,
                               PhotoShape photoShape, boolean showLogo, boolean showBookingLink, String customBody,
                               List<String> footerPictures, String disclaimer) {

    public SignatureContent {
        photoShape = photoShape == null ? PhotoShape.CIRCLE : photoShape;
        footerPictures = footerPictures == null ? List.of() : List.copyOf(footerPictures);
    }

    public SignatureContent(String name, SignatureLayout layout, SignatureFields fields, boolean showPhoto,
                            boolean showLogo, boolean showBookingLink, String customBody, String disclaimer) {
        this(name, layout, fields, showPhoto, PhotoShape.CIRCLE, showLogo, showBookingLink, customBody, List.of(),
                disclaimer);
    }

    public SignatureContent withoutBookingLink() {
        return new SignatureContent(name, layout, fields, showPhoto, photoShape, showLogo, false, customBody,
                footerPictures, disclaimer);
    }
}
