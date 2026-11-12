package app.lightmove.api.core.storage.constant;

import java.util.Locale;

/**
 * How a picture in an email is cut: the photo, or a picture the signer uploaded. The shape is baked into the picture,
 * since Outlook on Windows draws with Word's engine and ignores {@code border-radius}.
 */
public enum PictureShape {
    CIRCLE(0.5),
    ROUNDED(0.15),
    SQUARE(0);

    private final double cornerFraction;

    PictureShape(double cornerFraction) {
        this.cornerFraction = cornerFraction;
    }

    /** The corner radius as a share of the shorter edge: half of it is a circle. */
    public double cornerFraction() {
        return cornerFraction;
    }

    /** The shape a Markdown picture's title names among its words ({@code "small circle"}); square where none. */
    public static PictureShape fromTitle(String title) {
        if (title != null) {
            for (String word : title.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (word.equals("circle")) {
                    return CIRCLE;
                }
                if (word.equals("rounded")) {
                    return ROUNDED;
                }
            }
        }
        return SQUARE;
    }
}
