package app.lightmove.api.outreach.model;

/** A picture a person put in their Custom signature, as Uncava serves it, with the pixel size it was kept at. */
public record SignaturePicture(String url, int width, int height) {

    public static final int MAX_PER_SIGNATURE = 6;
}
