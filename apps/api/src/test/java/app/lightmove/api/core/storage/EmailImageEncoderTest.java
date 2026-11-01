package app.lightmove.api.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

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
        EncodedImage photo = EmailImageEncoder.photo(picture(1200, 800, "png")).orElseThrow();

        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        assertThat(photo.width()).isEqualTo(192);
        assertThat(photo.height()).isEqualTo(192);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(photo.content()));
        assertThat(decoded.getWidth()).isEqualTo(192);
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
        assertThat(EmailImageEncoder.photo("<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(EmailImageEncoder.photo(picture(20, 20, "gif"))).isEmpty();
        assertThat(EmailImageEncoder.logo(new byte[0])).isEmpty();
    }
}
