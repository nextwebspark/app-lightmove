package app.lightmove.api.mcp.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.security.apikey.PublicApiProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Holds an MCP request to a few kilobytes before anyone reads it: one declaring more is refused unread, and one sent
 * without a length stops being read at the cap. A JSON-RPC call is small; a large one is not a call.
 */
public class McpRequestLimitFilter extends OncePerRequestFilter {

    private final int maxBytes;
    private final PublicApiProblemWriter problems;

    public McpRequestLimitFilter(int maxBytes, PublicApiProblemWriter problems) {
        this.maxBytes = maxBytes;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            problems.write(request, response, ErrorCode.MCP_REQUEST_TOO_LARGE);
            return;
        }
        chain.doFilter(new BoundedRequest(request, maxBytes), response);
    }

    private static final class BoundedRequest extends HttpServletRequestWrapper {

        private final int maxBytes;
        private ServletInputStream bounded;

        BoundedRequest(HttpServletRequest request, int maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (bounded == null) {
                bounded = new BoundedInputStream(super.getInputStream(), maxBytes);
            }
            return bounded;
        }
    }

    private static final class BoundedInputStream extends ServletInputStream {

        private final ServletInputStream in;
        private final int maxBytes;
        private long read;

        BoundedInputStream(ServletInputStream in, int maxBytes) {
            this.in = in;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int next = in.read();
            if (next != -1) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int bytes) throws IOException {
            read += bytes;
            if (read > maxBytes) {
                throw new IOException("MCP request over " + maxBytes + " bytes");
            }
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
        }
    }
}
