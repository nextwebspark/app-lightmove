package app.lightmove.api.core.request.service;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;

/**
 * A request whose body stops at a cap while it is read, so a chunked body — which declares no length — is held to
 * it as surely as one that declares too many bytes.
 */
public class CappedBodyRequest extends HttpServletRequestWrapper {

    private final long maxBytes;
    private ServletInputStream capped;

    public CappedBodyRequest(HttpServletRequest request, long maxBytes) {
        super(request);
        this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (capped == null) {
            capped = new CappedInputStream(super.getInputStream(), maxBytes);
        }
        return capped;
    }

    private static final class CappedInputStream extends ServletInputStream {

        private final ServletInputStream body;
        private final long maxBytes;
        private long bytesRead;

        private CappedInputStream(ServletInputStream body, long maxBytes) {
            this.body = body;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int next = body.read();
            if (next >= 0) {
                countRead(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = body.read(buffer, offset, length);
            if (count > 0) {
                countRead(count);
            }
            return count;
        }

        private void countRead(int bytes) throws RequestBodyTooLargeException {
            bytesRead += bytes;
            if (bytesRead > maxBytes) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return body.isFinished();
        }

        @Override
        public boolean isReady() {
            return body.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            body.setReadListener(listener);
        }
    }
}
