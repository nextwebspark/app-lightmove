package app.lightmove.api;

import app.lightmove.api.core.security.avatar.PictureFetch;
import app.lightmove.api.core.security.avatar.PictureFetcher;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Answers every provider picture from memory, so no test reaches a CDN: each URL is a small picture of its own colour
 * (the same URL, the same bytes), unless a test took it down or broke it. The transport's guards are
 * {@code HttpPictureFetcherTest}'s.
 */
public class StubPictureFetcher implements PictureFetcher {

    private final Map<String, PictureFetch> answers = new ConcurrentHashMap<>();
    private final List<String> fetched = new CopyOnWriteArrayList<>();

    /** The link answers a refusal, as an expired LinkedIn link does. */
    public void takeDown(String url) {
        answers.put(url, new PictureFetch.Gone("HTTP 403"));
    }

    /** The provider is having a bad day. */
    public void breakDown(String url) {
        answers.put(url, new PictureFetch.Unavailable("HTTP 503"));
    }

    /** Two links, one picture: a provider re-signing the same photo's URL. */
    public void sameAs(String url, String original) {
        answers.put(url, pictureOf(original));
    }

    public List<String> fetched() {
        return List.copyOf(fetched);
    }

    @Override
    public PictureFetch fetch(URI pictureUrl) {
        String url = pictureUrl.toString();
        fetched.add(url);
        return answers.getOrDefault(url, pictureOf(url));
    }

    public static byte[] pngOf(Color colour, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(colour);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return out.toByteArray();
    }

    private static PictureFetch pictureOf(String url) {
        return new PictureFetch.Fetched(pngOf(new Color(url.hashCode() & 0xFFFFFF), 64, 64));
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public StubPictureFetcher stubPictureFetcher() {
            return new StubPictureFetcher();
        }
    }
}
