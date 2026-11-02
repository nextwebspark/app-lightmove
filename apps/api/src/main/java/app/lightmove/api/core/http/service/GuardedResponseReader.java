package app.lightmove.api.core.http.service;

import app.lightmove.api.core.http.model.GuardedResponse;
import java.io.IOException;

/** A caller's own reading of an answer; anything it throws but an {@link IOException} reaches the caller unchanged. */
@FunctionalInterface
public interface GuardedResponseReader<T> {

    T read(GuardedResponse response) throws IOException;
}
