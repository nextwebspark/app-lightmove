package app.lightmove.api.outreach.model;

import app.lightmove.api.core.storage.constant.PictureShape;

/** One picture in a signature's footer row: the signer's own upload, and the shape it is cut to. */
public record FooterPicture(String token, PictureShape shape) {

    public FooterPicture {
        shape = shape == null ? PictureShape.SQUARE : shape;
    }

    public String key() {
        return SignaturePicture.keyOf(token, shape);
    }
}
