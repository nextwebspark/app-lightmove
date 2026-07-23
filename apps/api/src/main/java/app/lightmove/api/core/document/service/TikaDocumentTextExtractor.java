package app.lightmove.api.core.document.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.io.ByteArrayInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/** Reads PDF, Word (doc/docx), and plain text through one library rather than branching per format. */
@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    @Override
    public String extract(byte[] content, String contentType) {
        // Unbounded (-1): a truncated body handler would silently drop the tail of a long brief, and
        // the upload is already size-capped upstream (lightmove.llm.max-document-size) — this is not a
        // second limit to maintain.
        BodyContentHandler handler = new BodyContentHandler(-1);
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            new AutoDetectParser().parse(stream, handler, new Metadata(), new ParseContext());
        } catch (java.io.IOException | SAXException | TikaException e) {
            throw new ApiException(ErrorCode.BRIEF_EXTRACTION_FAILED,
                    "Tika failed to parse a %s document".formatted(contentType));
        }

        String text = handler.toString().trim();
        if (text.isEmpty()) {
            throw new ApiException(ErrorCode.BRIEF_EXTRACTION_FAILED,
                    "Tika extracted no text from a %s document".formatted(contentType));
        }
        return text;
    }
}
