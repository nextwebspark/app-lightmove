package app.lightmove.api.positiontemplate.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A JSON file handed to the browser to save: a template export or the format's schema. */
final class PositionTemplateFileResponse {

    static final MediaType SCHEMA = MediaType.parseMediaType("application/schema+json");
    static final String SCHEMA_FILE_NAME = "position-templates.schema.json";

    private PositionTemplateFileResponse() {
    }

    static ResponseEntity<byte[]> attachment(byte[] body, MediaType type, String fileName) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
