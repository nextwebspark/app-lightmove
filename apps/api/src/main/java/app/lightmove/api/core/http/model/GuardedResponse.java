package app.lightmove.api.core.http.model;

import app.lightmove.api.core.http.constant.GuardedFetchFailureKind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;

/**
 * An answer as a caller reads it: everything but the body is free, and the body is read at most once and only up to the
 * cap, so a caller refusing on the status, the type or the declared length never reads a byte of it.
 */
public final class GuardedResponse {

    private final ClassicHttpResponse response;
    private final int maxBytes;
    private boolean bodyRead;

    public GuardedResponse(ClassicHttpResponse response, int maxBytes) {
        this.response = response;
        this.maxBytes = maxBytes;
    }

    public int status() {
        return response.getCode();
    }

    /** Null without a body. */
    public String contentType() {
        HttpEntity entity = response.getEntity();
        return entity == null ? null : entity.getContentType();
    }

    /** Negative when the host did not declare it. */
    public long declaredLength() {
        HttpEntity entity = response.getEntity();
        return entity == null ? -1 : entity.getContentLength();
    }

    public String header(String name) {
        Header header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    /**
     * Empty without a body; past the cap, {@link GuardedFetchFailureKind#TOO_LARGE} before reading any further. Not
     * closed on an overflow: closing reads the rest to reuse the connection, and the client aborts it instead.
     */
    public byte[] body() throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            bodyRead = true;
            return new byte[0];
        }
        if (entity.getContentLength() > maxBytes) {
            throw tooLarge(entity.getContentLength() + " bytes");
        }
        InputStream in = entity.getContent();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            body.write(buffer, 0, read);
            if (body.size() > maxBytes) {
                throw tooLarge("over " + maxBytes + " bytes");
            }
        }
        in.close();
        bodyRead = true;
        return body.toByteArray();
    }

    /** Whether the body was read to its end, so the connection may be kept rather than aborted. */
    public boolean isBodyRead() {
        return bodyRead;
    }

    private static GuardedFetchFailure tooLarge(String detail) {
        return new GuardedFetchFailure(GuardedFetchFailureKind.TOO_LARGE, detail, null);
    }
}
