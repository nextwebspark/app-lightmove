package app.lightmove.api.core.security.avatar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns whatever was sent into the one thing we store: a square JPEG of at most {@link #EDGE_PX}, decoded from a JPEG or
 * PNG and drawn again. Nothing of the original bytes survives — no metadata (a phone's GPS position included), no
 * markup, no second format hiding in the file — and anything else is refused rather than kept as it arrived.
 */
@Slf4j
final class AvatarImages {

    static final int EDGE_PX = 512;

    /** The header is read first: a few kilobytes can declare a raster of gigabytes. */
    private static final int MAX_EDGE_PX = 12_000;
    private static final long MAX_PIXELS = 60_000_000L;

    /** Larger pictures are read at a fraction of their resolution, so the raster held stays near this. */
    private static final int DECODE_TARGET_PX = 2 * EDGE_PX;

    private static final Set<String> READABLE_FORMATS = Set.of("jpeg", "png");
    private static final float JPEG_QUALITY = 0.85f;

    private AvatarImages() {
    }

    /** Null where the bytes are not a JPEG or PNG this JVM can decode, or declare an absurd size. */
    static AvatarPicture normalise(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        try {
            BufferedImage source = decode(content);
            if (source == null) {
                return null;
            }
            byte[] jpeg = encodeJpeg(squared(source));
            return jpeg == null ? null : new AvatarPicture(jpeg, sha256(jpeg));
        } catch (IOException | RuntimeException | OutOfMemoryError unreadable) {
            log.info("Refusing a profile photo that does not decode: {}", unreadable.getMessage());
            return null;
        }
    }

    private static BufferedImage decode(byte[] content) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                if (!READABLE_FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    return null;
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || Math.max(width, height) > MAX_EDGE_PX
                        || (long) width * height > MAX_PIXELS) {
                    return null;
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int subsampling = Math.max(1, Math.min(width, height) / DECODE_TARGET_PX);
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** The middle square, scaled down in halving steps so a large picture does not shimmer; flattened onto white. */
    private static BufferedImage squared(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage current = source.getSubimage(
                (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        int target = Math.min(side, EDGE_PX);
        int size = side;
        do {
            size = Math.max(target, size / 2);
            current = drawn(current, size);
        } while (size > target);
        return current;
    }

    private static BufferedImage drawn(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(AvatarPicture.CONTENT_TYPE);
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(encoded)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return encoded.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
