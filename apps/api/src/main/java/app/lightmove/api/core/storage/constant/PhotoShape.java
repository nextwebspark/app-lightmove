package app.lightmove.api.core.storage.constant;

/**
 * How a profile photo is cut for an email. The shape is baked into the picture, since Outlook on Windows draws with
 * Word's engine and ignores {@code border-radius}.
 */
public enum PhotoShape {
    CIRCLE(0.5),
    ROUNDED(0.15),
    SQUARE(0);

    private final double cornerFraction;

    PhotoShape(double cornerFraction) {
        this.cornerFraction = cornerFraction;
    }

    /** The corner radius as a share of the edge: half of it is a circle. */
    public double cornerFraction() {
        return cornerFraction;
    }
}
