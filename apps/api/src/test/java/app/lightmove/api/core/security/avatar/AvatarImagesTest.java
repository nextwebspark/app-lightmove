package app.lightmove.api.core.security.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.StubPictureFetcher;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvatarImagesTest {

    @Test
    @DisplayName("a wide PNG becomes its middle square, 512px, as JPEG")
    void squaresAndShrinks() throws IOException {
        BufferedImage wide = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 1200; x++) {
            for (int y = 0; y < 600; y++) {
                wide.setRGB(x, y, x < 300 || x >= 900 ? Color.RED.getRGB() : Color.BLUE.getRGB());
            }
        }

        AvatarPicture picture = AvatarImages.normalise(encode(wide, "png"));

        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(picture.jpeg()));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(512);
        // The red bands either side are cropped away: every edge pixel is the blue middle.
        Color corner = new Color(stored.getRGB(2, 2));
        assertThat(corner.getBlue()).isGreaterThan(200);
        assertThat(corner.getRed()).isLessThan(60);
        assertThat(picture.jpeg()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(picture.jpeg()[1] & 0xFF).isEqualTo(0xD8);
        assertThat(picture.sha256()).hasSize(64);
    }

    @Test
    @DisplayName("a small picture is not blown up, and a transparent one is flattened onto white")
    void keepsSmallPicturesSmallAndFlattens() throws IOException {
        BufferedImage clear = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);

        AvatarPicture picture = AvatarImages.normalise(encode(clear, "png"));

        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(picture.jpeg()));
        assertThat(stored.getWidth()).isEqualTo(80);
        assertThat(new Color(stored.getRGB(40, 40)).getRed()).isGreaterThan(240);
    }

    @Test
    @DisplayName("nothing of the original file survives: text hidden in it is gone")
    void dropsMetadata() throws IOException {
        byte[] png = withTextChunk(StubPictureFetcher.pngOf(Color.GRAY, 60, 60), "GPSLatitude=25.2048");

        AvatarPicture picture = AvatarImages.normalise(png);

        assertThat(new String(picture.jpeg(), StandardCharsets.ISO_8859_1)).doesNotContain("GPSLatitude");
    }

    @Test
    @DisplayName("the same picture twice is the same bytes, so a re-sent photo keeps its link")
    void isDeterministic() {
        byte[] png = StubPictureFetcher.pngOf(Color.CYAN, 200, 300);

        assertThat(AvatarImages.normalise(png).sha256()).isEqualTo(AvatarImages.normalise(png).sha256());
    }

    @Test
    @DisplayName("SVG, GIF, text, an empty file and an absurd declared size are all refused")
    void refusesWhatItShouldNotKeep() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] gif = encode(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "gif");

        assertThat(AvatarImages.normalise(svg)).isNull();
        assertThat(AvatarImages.normalise(gif)).isNull();
        assertThat(AvatarImages.normalise("hello".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(AvatarImages.normalise(new byte[0])).isNull();
        assertThat(AvatarImages.normalise(null)).isNull();
        assertThat(AvatarImages.normalise(pngDeclaring(20_000, 20_000))).isNull();
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** A real PNG header declaring a size, and no pixels: a few bytes asking for gigabytes of raster. */
    private static byte[] pngDeclaring(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(chunk("IHDR", ihdr.array()));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    /** Inserts a tEXt chunk after IHDR (8-byte signature + 25-byte IHDR chunk). */
    private static byte[] withTextChunk(byte[] png, String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 33);
        out.writeBytes(chunk("tEXt", ("Comment\0" + text).getBytes(StandardCharsets.ISO_8859_1)));
        out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
                .putInt((int) crc.getValue()).array();
    }
}
