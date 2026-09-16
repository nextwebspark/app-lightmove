package app.lightmove.api.position.service;

import app.lightmove.api.core.config.PositionExtractionSettings;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The catch-all: anything no other {@link PositionDocumentFormatReader} claimed is read as UTF-8
 * text. Ordered last — every other reader answers a real signature check, and this one always
 * answers {@code true}, so a format reader added later only needs to sit ahead of this one, never
 * behind it.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class PlainTextFormatReader implements PositionDocumentFormatReader {

    @Override
    public boolean supports(byte[] content) {
        return true;
    }

    @Override
    public String extractText(byte[] content, PositionExtractionSettings settings) {
        return new String(content, StandardCharsets.UTF_8);
    }
}
