package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.EnrichedPhoto;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a CDN sends is not what an avatar needs: a photo is stored at the size the grid draws it. */
class ProfilePhotoThumbnailTest {

    @Test
    @DisplayName("a full-size photo is stored as a JPEG within the avatar's edge")
    void shrinksALargePhoto() throws IOException {
        EnrichedPhoto original = photo(1_000, 1_000, "png");

        EnrichedPhoto stored = ProfilePhotoThumbnail.shrink(original);

        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(longestEdgeOf(stored)).isEqualTo(ProfilePhotoThumbnail.MAX_EDGE_PX);
        assertThat(stored.content().length).isLessThan(original.content().length);
    }

    @Test
    @DisplayName("a photo already small enough is stored as it arrived")
    void leavesASmallPhotoAlone() throws IOException {
        EnrichedPhoto original = photo(120, 120, "jpg");

        EnrichedPhoto stored = ProfilePhotoThumbnail.shrink(original);

        assertThat(stored.content()).isSameAs(original.content());
    }

    @Test
    @DisplayName("a rectangular photo keeps its proportions")
    void keepsTheAspectRatio() throws IOException {
        EnrichedPhoto stored = ProfilePhotoThumbnail.shrink(photo(800, 400, "png"));

        BufferedImage decoded = decode(stored);
        assertThat(decoded.getWidth()).isEqualTo(ProfilePhotoThumbnail.MAX_EDGE_PX);
        assertThat(decoded.getHeight()).isEqualTo(ProfilePhotoThumbnail.MAX_EDGE_PX / 2);
    }

    @Test
    @DisplayName("bytes no reader here understands are stored rather than lost")
    void passesThroughWhatItCannotDecode() {
        // WEBP is in the allowlist and has no reader on a stock JDK. The download is already capped,
        // so the honest answer is the bytes as they arrived — a browser will very likely render them.
        EnrichedPhoto original = new EnrichedPhoto("not an image".getBytes(), "image/webp");

        assertThat(ProfilePhotoThumbnail.shrink(original)).isSameAs(original);
    }

    @Test
    @DisplayName("a picture whose dimensions are absurd is refused before it is decoded")
    void refusesADecompressionBomb() throws IOException {
        // A bounded download is not a bounded raster: 9000x9000 compresses small and decodes to
        // hundreds of megabytes, which is the allocation the header check exists to refuse.
        EnrichedPhoto bomb = photo(9_000, 9_000, "png");

        assertThat(ProfilePhotoThumbnail.shrink(bomb)).isNull();
    }

    private static EnrichedPhoto photo(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setColor(Color.BLUE);
        canvas.fillRect(0, 0, width, height);
        canvas.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return new EnrichedPhoto(bytes.toByteArray(), "png".equals(format) ? "image/png" : "image/jpeg");
    }

    private static BufferedImage decode(EnrichedPhoto photo) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(photo.content()));
    }

    private static int longestEdgeOf(EnrichedPhoto photo) throws IOException {
        BufferedImage decoded = decode(photo);
        return Math.max(decoded.getWidth(), decoded.getHeight());
    }
}
