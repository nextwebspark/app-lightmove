package app.uncava.api.core.request.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Holds a JSON request body to the cap as it is read, declared length or none. The refusal surfaces where the body
 * is bound, so {@code GlobalExceptionHandler} answers it as {@code REQUEST_TOO_LARGE} like any other problem.
 */
@RequiredArgsConstructor
public class JsonBodySizeFilter extends OncePerRequestFilter {

    private final DataSize maxJsonBodySize;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(isJson(request.getContentType())
                ? new CappedBodyRequest(request, maxJsonBodySize.toBytes())
                : request, response);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.includes(type) || type.getSubtype().endsWith("+json");
        } catch (IllegalArgumentException unparseable) {
            return false;
        }
    }
}
