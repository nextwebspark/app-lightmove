package app.lightmove.api.core.request.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuses a JSON body whose declared length passes the cap before anything reads it. An interceptor, so the
 * refusal is {@code GlobalExceptionHandler}'s problem body like any other.
 */
@RequiredArgsConstructor
public class JsonBodySizeLimit implements HandlerInterceptor {

    private final DataSize maxJsonBodySize;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isJson(request.getContentType()) && request.getContentLengthLong() > maxJsonBodySize.toBytes()) {
            throw new ApiException(ErrorCode.REQUEST_TOO_LARGE,
                    "JSON body of " + request.getContentLengthLong() + " bytes");
        }
        return true;
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
