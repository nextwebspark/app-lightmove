package app.uncava.api.core.request.service;

import java.io.IOException;

/** Thrown from a request body's stream once it has given more bytes than the cap allows. */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(long maxBytes) {
        super("request body passed " + maxBytes + " bytes");
    }
}
