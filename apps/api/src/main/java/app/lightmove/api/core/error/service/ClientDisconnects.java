package app.lightmove.api.core.error.service;

import jakarta.servlet.ServletRequest;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * Recognises a client that hung up mid-response — routine (the SPA cancels superseded reads), never a
 * bug — and marks the request, so the request-completion line records 499 rather than a phantom status.
 */
public final class ClientDisconnects {

    private static final String CLIENT_GONE_ATTRIBUTE = ClientDisconnects.class.getName() + ".GONE";

    private static final Pattern DISCONNECT_MESSAGE =
            Pattern.compile("Broken pipe|Connection reset|An established connection was aborted",
                    Pattern.CASE_INSENSITIVE);

    private ClientDisconnects() {
    }

    /** Walks the cause chain: the socket's {@code IOException} arrives wrapped in {@code ClientAbortException} and more. */
    public static boolean isDisconnect(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException
                    || cause.getClass().getSimpleName().equals("ClientAbortException")) {
                return true;
            }
            if (cause instanceof IOException && DISCONNECT_MESSAGE.matcher(String.valueOf(cause.getMessage())).find()) {
                return true;
            }
            if (cause.getCause() == cause) {
                return false;
            }
        }
        return false;
    }

    public static void markGone(ServletRequest request) {
        request.setAttribute(CLIENT_GONE_ATTRIBUTE, Boolean.TRUE);
    }

    public static boolean isMarkedGone(ServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(CLIENT_GONE_ATTRIBUTE));
    }
}
