package app.lightmove.api.core.storage.service;

import app.lightmove.api.core.storage.model.EncodedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Decodes a picture and writes it again at the size an email draws it, twice over for a sharp screen. Only the pixels
 * survive: whatever a camera or an editor wrote beside them — a location, a name — is never read back out. Empty for
 * anything that is not a JPEG or PNG this JDK can read, or whose header claims a raster too large to decode safely.
 */
public final class EmailImageEncoder {

    /** A photo is drawn 64px square; kept at three times that. */
    static final int PHOTO_EDGE_PX = 192;

    /** A logo is drawn at most 200 × 60 or so; kept at twice the box it may fill. */
    static final int LOGO_MAX_WIDTH_PX = 480;
    static final int LOGO_MAX_HEIGHT_PX = 160;

    /** Beyond this, a bounded upload still decodes to hundreds of megabytes of raster. */
    private static final int MAX_DECODABLE_EDGE_PX = 8_000;
    private static final float JPEG_QUALITY = 0.88f;

    private EmailImageEncoder() {
    }

    /** A square, centred crop, as JPEG on white: a photo has no transparency worth keeping. */
    public static Optional<EncodedImage> photo(byte[] content) {
        return decode(content).map(source -> {
            int edge = Math.min(source.getWidth(), source.getHeight());
            BufferedImage square = source.getSubimage((source.getWidth() - edge) / 2, (source.getHeight() - edge) / 2,
                    edge, edge);
            int target = Math.min(PHOTO_EDGE_PX, edge);
            BufferedImage scaled = scaled(square, target, target, BufferedImage.TYPE_INT_RGB);
            return new EncodedImage(jpeg(scaled), "image/jpeg", target, target);
        });
    }

    /** Fitted inside the logo box, never enlarged, as PNG so a transparent logo stays transparent. */
    public static Optional<EncodedImage> logo(byte[] content) {
        return decode(content).map(source -> {
            double scale = Math.min(1.0, Math.min((double) LOGO_MAX_WIDTH_PX / source.getWidth(),
                    (double) LOGO_MAX_HEIGHT_PX / source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage scaled = scaled(source, width, height, BufferedImage.TYPE_INT_ARGB);
            return new EncodedImage(png(scaled), "image/png", width, height);
        });
    }

    private static Optional<BufferedImage> decode(byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        try {
            if (!isReadableAndBounded(content)) {
                return Optional.empty();
            }
            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(content)));
        } catch (IOException | RuntimeException | OutOfMemoryError unreadable) {
            return Optional.empty();
        }
    }

    /** Reads the header only: {@code ImageIO.read} allocates the whole raster before it could be measured. */
    private static boolean isReadableAndBounded(byte[] content) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    return false;
                }
                reader.setInput(stream);
                return Math.max(reader.getWidth(0), reader.getHeight(0)) <= MAX_DECODABLE_EDGE_PX;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Halves in steps before the last pass: one bicubic step from thousands of pixels to dozens drops detail. */
    private static BufferedImage scaled(BufferedImage source, int width, int height, int type) {
        BufferedImage current = source;
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();
        do {
            currentWidth = Math.max(width, currentWidth / 2);
            currentHeight = Math.max(height, currentHeight / 2);
            if (currentWidth < width * 2 || currentHeight < height * 2) {
                currentWidth = width;
                currentHeight = height;
            }
            BufferedImage step = new BufferedImage(currentWidth, currentHeight, type);
            Graphics2D graphics = step.createGraphics();
            try {
                if (type == BufferedImage.TYPE_INT_RGB) {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, currentWidth, currentHeight);
                }
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(current, 0, 0, currentWidth, currentHeight, null);
            } finally {
                graphics.dispose();
            }
            current = step;
        } while (currentWidth != width || currentHeight != height);
        return current;
    }

    private static byte[] jpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException impossible) {
            throw new IllegalStateException("Writing a JPEG to memory failed", impossible);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException impossible) {
            throw new IllegalStateException("Writing a PNG to memory failed", impossible);
        }
        return out.toByteArray();
    }
}
