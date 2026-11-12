package app.lightmove.api.outreach.constant;

import java.util.Locale;

/**
 * How large a Custom signature draws one of its pictures: the box it is fitted inside, never enlarged past the pixels it
 * was kept at. Written as the picture's Markdown title ({@code ![](… "small")}); anything else is {@link #MEDIUM}.
 */
public enum SignaturePictureSize {
    SMALL(200, 40),
    MEDIUM(300, 64),
    LARGE(600, 200);

    private final int maxWidthPx;
    private final int maxHeightPx;

    SignaturePictureSize(int maxWidthPx, int maxHeightPx) {
        this.maxWidthPx = maxWidthPx;
        this.maxHeightPx = maxHeightPx;
    }

    public static SignaturePictureSize fromTitle(String title) {
        if (title == null) {
            return MEDIUM;
        }
        for (String word : title.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.equals("small")) {
                return SMALL;
            }
            if (word.equals("large")) {
                return LARGE;
            }
        }
        return MEDIUM;
    }

    /** {@code width} and {@code height} fitted inside this box, as {@code [width, height]}. */
    public int[] fit(int width, int height) {
        double scale = Math.min(1.0, Math.min((double) maxWidthPx / width, (double) maxHeightPx / height));
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }
}
