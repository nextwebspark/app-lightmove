package app.lightmove.api.outreach.model;

import app.lightmove.api.core.storage.constant.PictureShape;
import java.util.Map;

/**
 * What a consultant's signatures follow where they override nothing: their profile, their workspace, the mailbox they
 * send from and their booking link. {@code photos} holds the photo in each shape a drawing asks for; {@code logo} and
 * {@code bookingLink} are null where there is none; a photo or logo is always one Uncava serves, never another host's.
 * {@code pictures} are the signer's own uploaded pictures a drawing names, keyed by token.
 */
public record SignatureSender(String name, String title, String phone, String linkedinUrl, String email,
                              String company, String website, Map<PictureShape, String> photos, SignatureLogo logo,
                              String bookingLink, Map<String, SignaturePicture> pictures) {

    public SignatureSender {
        photos = photos == null ? Map.of() : Map.copyOf(photos);
        pictures = pictures == null ? Map.of() : Map.copyOf(pictures);
    }

    /** Null where the person has no photo. */
    public String photoUrl(PictureShape shape) {
        return photos.get(shape);
    }
}
