package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedPhoto;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * A downloaded photo as something an avatar needs: at most {@link #MAX_EDGE_PX} on its longest edge,
 * re-encoded as JPEG. The grid draws these in 24px circles and a page holds fifty of them, so storing
 * what a CDN happens to serve would put megabytes on the wire for a thumbnail.
 *
 * <p>Every failure answers with the bytes as they arrived rather than nothing: an image this cannot
 * decode is still an image the browser probably can. The one exception is a picture whose dimensions
 * are absurd — those are refused outright, because the buffer is bounded while the pixels it decodes
 * to are not.
 */
@Slf4j
final class ProfilePhotoThumbnail {

    static final int MAX_EDGE_PX = 256;

    /** Beyond this, a bounded download still decodes to hundreds of megabytes of raster. */
    private static final int MAX_DECODABLE_EDGE_PX = 8_000;

    private static final String JPEG = "image/jpeg";
    private static final float JPEG_QUALITY = 0.85f;

    private ProfilePhotoThumbnail() {}

    /** The photo shrunk, the photo untouched, or null where the picture is refused outright. */
    static EnrichedPhoto shrink(EnrichedPhoto photo) {
        try {
            if (!withinDecodableBounds(photo.content())) {
                return null;
            }
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(photo.content()));
            if (source == null) {
                // No reader for this format on this JDK — WEBP, today. The bytes are already capped.
                return photo;
            }
            int longestEdge = Math.max(source.getWidth(), source.getHeight());
            if (longestEdge <= MAX_EDGE_PX && JPEG.equals(photo.contentType())) {
                return photo;
            }
            byte[] encoded = encodeJpeg(scaled(source, longestEdge));
            return encoded == null ? photo : new EnrichedPhoto(encoded, JPEG);
        } catch (IOException | RuntimeException | OutOfMemoryError failed) {
            log.info("Storing a profile photo as it arrived: {}", failed.getMessage());
            return photo;
        }
    }

    /**
     * The header alone, read without decoding a pixel. {@code ImageIO.read} would allocate the whole
     * raster before anything here could measure it, which is the allocation being guarded against.
     */
    private static boolean withinDecodableBounds(byte[] content) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                return true;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return true;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                if (Math.max(reader.getWidth(0), reader.getHeight(0)) > MAX_DECODABLE_EDGE_PX) {
                    log.info("Refusing a profile photo of {}x{}", reader.getWidth(0), reader.getHeight(0));
                    return false;
                }
                return true;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Alpha flattened onto white: a JPEG has no transparency, and the default fill is black. */
    private static BufferedImage scaled(BufferedImage source, int longestEdge) {
        double factor = Math.min(1d, (double) MAX_EDGE_PX / longestEdge);
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = target.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            canvas.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            canvas.setColor(Color.WHITE);
            canvas.fillRect(0, 0, width, height);
            canvas.drawImage(source, 0, 0, width, height, null);
        } finally {
            canvas.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(JPEG);
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
