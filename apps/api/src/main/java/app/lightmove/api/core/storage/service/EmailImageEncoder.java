package app.lightmove.api.core.storage.service;

import app.lightmove.api.core.storage.constant.PhotoShape;
import app.lightmove.api.core.storage.model.EncodedImage;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
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

    /** A signature's own picture is drawn at most 600 × 200; kept at twice that. */
    static final int SIGNATURE_PICTURE_MAX_WIDTH_PX = 1200;
    static final int SIGNATURE_PICTURE_MAX_HEIGHT_PX = 400;

    /** Past this a picture is refused before its pixels are read. */
    static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.88f;

    private EmailImageEncoder() {
    }

    /**
     * A square, centred crop. Square is JPEG on white; a circle or rounded square is PNG with its corners transparent,
     * the shape in the pixels so every mail client draws it.
     */
    public static Optional<EncodedImage> photo(byte[] content, PhotoShape shape) {
        return decode(content, PHOTO_EDGE_PX).map(source -> {
            int edge = Math.min(source.getWidth(), source.getHeight());
            BufferedImage square = source.getSubimage((source.getWidth() - edge) / 2, (source.getHeight() - edge) / 2,
                    edge, edge);
            int target = Math.min(PHOTO_EDGE_PX, edge);
            if (shape == PhotoShape.SQUARE) {
                BufferedImage scaled = scaled(square, target, target, BufferedImage.TYPE_INT_RGB);
                return new EncodedImage(jpeg(scaled), "image/jpeg", target, target);
            }
            return new EncodedImage(png(cut(scaled(square, target, target, BufferedImage.TYPE_INT_ARGB), shape)),
                    "image/png", target, target);
        });
    }

    /** Drawn through an antialiased mask, so the edge is smooth rather than stepped. */
    private static BufferedImage cut(BufferedImage square, PhotoShape shape) {
        int edge = square.getWidth();
        double arc = edge * shape.cornerFraction() * 2;
        BufferedImage shaped = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = shaped.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fill(new RoundRectangle2D.Double(0, 0, edge, edge, arc, arc));
            graphics.setComposite(AlphaComposite.SrcIn);
            graphics.drawImage(square, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return shaped;
    }

    /** Fitted inside the logo box, never enlarged, as PNG so a transparent logo stays transparent. */
    public static Optional<EncodedImage> logo(byte[] content) {
        return decode(content, LOGO_MAX_WIDTH_PX).map(source -> {
            double scale = Math.min(1.0, Math.min((double) LOGO_MAX_WIDTH_PX / source.getWidth(),
                    (double) LOGO_MAX_HEIGHT_PX / source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage scaled = scaled(source, width, height, BufferedImage.TYPE_INT_ARGB);
            return new EncodedImage(png(scaled), "image/png", width, height);
        });
    }

    /**
     * A Custom signature's own picture, fitted inside twice the largest box a signature draws one in and never enlarged:
     * PNG where it has transparency to keep, JPEG otherwise, so a photo-like banner stays small.
     */
    public static Optional<EncodedImage> signaturePicture(byte[] content) {
        return decode(content, SIGNATURE_PICTURE_MAX_WIDTH_PX).map(source -> {
            double scale = Math.min(1.0, Math.min((double) SIGNATURE_PICTURE_MAX_WIDTH_PX / source.getWidth(),
                    (double) SIGNATURE_PICTURE_MAX_HEIGHT_PX / source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            if (source.getColorModel().hasAlpha()) {
                return new EncodedImage(png(scaled(source, width, height, BufferedImage.TYPE_INT_ARGB)), "image/png",
                        width, height);
            }
            return new EncodedImage(jpeg(scaled(source, width, height, BufferedImage.TYPE_INT_RGB)), "image/jpeg",
                    width, height);
        });
    }

    /**
     * Read from the header first and refused past {@link #MAX_PIXELS} — a few kilobytes of flat-colour PNG can declare a
     * raster of gigabytes — then decoded at a fraction of its resolution, so the raster held stays near twice the size
     * it is drawn at whatever the picture declares.
     */
    private static Optional<BufferedImage> decode(byte[] content, int largestTargetEdge) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    return Optional.empty();
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    return Optional.empty();
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int subsampling = Math.max(1, Math.max(width, height) / (2 * largestTargetEdge));
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                return Optional.ofNullable(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
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
