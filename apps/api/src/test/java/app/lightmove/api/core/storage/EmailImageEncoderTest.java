package app.lightmove.api.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.storage.constant.PictureShape;
import app.lightmove.api.core.storage.model.EncodedImage;
import app.lightmove.api.core.storage.service.EmailImageEncoder;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A picture comes out at the size an email draws it, re-encoded, or not at all. */
class EmailImageEncoderTest {

    static byte[] picture(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("a photo is cropped square and kept at 192 pixels as JPEG")
    void photoIsSquare() throws Exception {
        EncodedImage photo = EmailImageEncoder.photo(picture(1200, 800, "png"), PictureShape.SQUARE).orElseThrow();

        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        assertThat(photo.width()).isEqualTo(192);
        assertThat(photo.height()).isEqualTo(192);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(photo.content()));
        assertThat(decoded.getWidth()).isEqualTo(192);
    }

    @Test
    @DisplayName("a circle or rounded photo is PNG with its corners cut away, its middle kept")
    void shapedPhotoHasTransparentCorners() throws Exception {
        for (PictureShape shape : new PictureShape[] {PictureShape.CIRCLE, PictureShape.ROUNDED}) {
            EncodedImage photo = EmailImageEncoder.photo(picture(1200, 800, "jpeg"), shape).orElseThrow();

            assertThat(photo.contentType()).isEqualTo("image/png");
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(photo.content()));
            assertThat(decoded.getRGB(0, 0) >>> 24).as(shape + " corner").isZero();
            assertThat(decoded.getRGB(96, 96) >>> 24).as(shape + " middle").isEqualTo(255);
        }
    }

    @Test
    @DisplayName("an upload cut to a circle is its centred square; rounded keeps its proportions; both cut their corners")
    void shapedUpload() throws Exception {
        EncodedImage circle = EmailImageEncoder.shaped(picture(800, 400, "png"), PictureShape.CIRCLE).orElseThrow();
        assertThat(circle.width()).isEqualTo(circle.height()).isEqualTo(400);
        EncodedImage rounded = EmailImageEncoder.shaped(picture(800, 400, "png"), PictureShape.ROUNDED).orElseThrow();
        assertThat(rounded.width()).isEqualTo(800);
        assertThat(rounded.height()).isEqualTo(400);

        // A screenshot is kept as JPEG; its shape is cut from that copy.
        EncodedImage kept = EmailImageEncoder.signaturePicture(picture(1600, 1000, "jpeg")).orElseThrow();
        assertThat(kept.contentType()).isEqualTo("image/jpeg");
        EncodedImage fromJpeg = EmailImageEncoder.shaped(kept.content(), PictureShape.CIRCLE).orElseThrow();
        assertThat(fromJpeg.width()).isEqualTo(fromJpeg.height());

        for (EncodedImage shaped : new EncodedImage[] {circle, rounded, fromJpeg}) {
            assertThat(shaped.contentType()).isEqualTo("image/png");
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(shaped.content()));
            assertThat(decoded.getRGB(0, 0) >>> 24).isZero();
            assertThat(decoded.getRGB(decoded.getWidth() / 2, decoded.getHeight() / 2) >>> 24).isEqualTo(255);
        }
    }

    @Test
    @DisplayName("a logo fits its box as PNG and is never enlarged")
    void logoFits() throws Exception {
        EncodedImage wide = EmailImageEncoder.logo(picture(2000, 400, "png")).orElseThrow();
        assertThat(wide.contentType()).isEqualTo("image/png");
        assertThat(wide.width()).isEqualTo(480);
        assertThat(wide.height()).isEqualTo(96);

        EncodedImage small = EmailImageEncoder.logo(picture(120, 40, "jpeg")).orElseThrow();
        assertThat(small.width()).isEqualTo(120);
        assertThat(small.height()).isEqualTo(40);
    }

    @Test
    @DisplayName("anything but a JPEG or PNG is refused")
    void refusesTheRest() throws Exception {
        assertThat(EmailImageEncoder.photo("<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8),
                PictureShape.CIRCLE)).isEmpty();
        assertThat(EmailImageEncoder.photo(picture(20, 20, "gif"), PictureShape.CIRCLE)).isEmpty();
        assertThat(EmailImageEncoder.logo(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("only the pixels survive: a JPEG's EXIF and a PNG's text chunks are not written back out")
    void metadataStripped() throws Exception {
        byte[] secret = "GPS 25.2048N 55.2708E by Yara".getBytes(StandardCharsets.ISO_8859_1);

        byte[] jpeg = picture(300, 300, "jpeg");
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        byte[] exif = concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), secret);
        withExif.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) ((exif.length + 2) >> 8), (byte) (exif.length + 2)});
        withExif.write(exif);
        withExif.write(jpeg, 2, jpeg.length - 2);
        assertThat(contains(withExif.toByteArray(), secret)).isTrue();
        assertThat(contains(EmailImageEncoder.photo(withExif.toByteArray(), PictureShape.SQUARE).orElseThrow().content(),
                secret)).isFalse();

        byte[] png = picture(300, 100, "png");
        byte[] withText = concat(java.util.Arrays.copyOfRange(png, 0, 33),
                chunk("tEXt", concat("Author\0".getBytes(StandardCharsets.ISO_8859_1), secret)),
                java.util.Arrays.copyOfRange(png, 33, png.length));
        assertThat(contains(withText, secret)).isTrue();
        assertThat(contains(EmailImageEncoder.logo(withText).orElseThrow().content(), secret)).isFalse();
    }

    @Test
    @DisplayName("a small file whose header claims a raster past the cap is refused before its pixels are read")
    void declaredOversizeRefused() throws Exception {
        byte[] png = picture(40, 40, "png");
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(9_000).putInt(9_000);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(png, 12, 17);
        java.nio.ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());

        assertThat(png.length).isLessThan(2_000);
        assertThat(EmailImageEncoder.photo(png, PictureShape.CIRCLE)).isEmpty();
        assertThat(EmailImageEncoder.logo(png)).isEmpty();
    }

    @Test
    @DisplayName("a signature's own picture is fitted inside twice its largest box, a transparent one kept as PNG")
    void signaturePictureFitsAndKeepsTransparency() throws Exception {
        EncodedImage logo = EmailImageEncoder.signaturePicture(picture(4000, 1000, "png")).orElseThrow();

        assertThat(logo.contentType()).isEqualTo("image/png");
        assertThat(logo.width()).isEqualTo(1200);
        assertThat(logo.height()).isEqualTo(300);
    }

    @Test
    @DisplayName("a signature's opaque picture becomes a JPEG, a small one keeps its size, and a non-picture is refused")
    void signaturePictureOpaqueSmallOrRefused() throws Exception {
        EncodedImage badge = EmailImageEncoder.signaturePicture(picture(240, 80, "jpg")).orElseThrow();

        assertThat(badge.contentType()).isEqualTo("image/jpeg");
        assertThat(badge.width()).isEqualTo(240);
        assertThat(badge.height()).isEqualTo(80);
        assertThat(EmailImageEncoder.signaturePicture("<svg/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(EmailImageEncoder.signaturePicture(picture(40, 40, "gif"))).isEmpty();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return java.nio.ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
                .putInt((int) crc.getValue()).array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
