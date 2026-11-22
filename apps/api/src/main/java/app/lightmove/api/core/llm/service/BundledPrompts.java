package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.BundledPrompt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * The prompts this build ships, read from {@code prompts/} on the classpath: what serves while the registry is off
 * or unreadable, and what the boot sync records. A prompt's id is its file name without {@code -system.st}, or
 * without {@code .st} where it has no such suffix.
 */
@Component
public class BundledPrompts {

    private static final String FOLDER = "prompts/";
    private static final String STRUCTURED_SUFFIX = "-system.st";
    private static final String SUFFIX = ".st";

    public String bodyOf(String promptId) {
        Resource structured = new ClassPathResource(FOLDER + promptId + STRUCTURED_SUFFIX);
        return read(structured.exists() ? structured : new ClassPathResource(FOLDER + promptId + SUFFIX));
    }

    public List<BundledPrompt> all() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:" + FOLDER + "*" + SUFFIX);
            List<BundledPrompt> prompts = new ArrayList<>(files.length);
            for (Resource file : files) {
                String body = read(file);
                prompts.add(new BundledPrompt(idOf(file.getFilename()), body, sha256Of(body)));
            }
            prompts.sort(Comparator.comparing(BundledPrompt::promptId));
            return prompts;
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Could not list the bundled prompts", unreadable);
        }
    }

    public static String sha256Of(String body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String idOf(String fileName) {
        String suffix = fileName.endsWith(STRUCTURED_SUFFIX) ? STRUCTURED_SUFFIX : SUFFIX;
        return fileName.substring(0, fileName.length() - suffix.length());
    }

    private static String read(Resource file) {
        try {
            return file.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Could not read the bundled prompt " + file.getDescription(), unreadable);
        }
    }
}
