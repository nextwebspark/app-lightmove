package app.lightmove.api.outreach.model;

import app.lightmove.api.core.storage.constant.PictureShape;

/** A picture a person put in their signature, as Uncava serves it in its shape, with the pixel size it was kept at. */
public record SignaturePicture(String url, int width, int height) {

    public static final int MAX_PER_SIGNATURE = 6;

    /** How a drawing names an upload in a shape: its token alone for a square, the shape after a colon otherwise. */
    public static String keyOf(String token, PictureShape shape) {
        return shape == PictureShape.SQUARE ? token : token + ":" + shape.name();
    }

    /** The upload's token a {@link #keyOf key} names. */
    public static String tokenOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(0, colon);
    }

    /** The shape a {@link #keyOf key} names. */
    public static PictureShape shapeOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? PictureShape.SQUARE : PictureShape.valueOf(key.substring(colon + 1));
    }
}
