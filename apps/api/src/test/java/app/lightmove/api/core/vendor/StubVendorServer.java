package app.lightmove.api.core.vendor;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A real HTTP server that answers whatever the test scripts, on a real socket.
 *
 * <p>Deliberately not {@code MockRestServiceServer}. That works by replacing the request factory on a
 * {@code RestClient.Builder} — and {@code VendorClientFactory} installs its own, timeout-bound
 * factory, which would overwrite the mock's and send the test at the open internet. The choice was
 * between weakening the guarantee that no vendor client can ship without timeouts, and using a real
 * socket. The socket is cheaper: it comes with the JDK, needs no new dependency, and exercises the
 * status handler, the error body and the header parsing exactly as production will.
 *
 * <p>Counting requests is most of the point. Nearly every question worth asking of this layer is
 * "how many times did we pay for that?".
 */
public final class StubVendorServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<Response> scripted = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();

    /** The last scripted response repeats once the queue runs dry, so a test can say "always 503". */
    private Response lastServed;

    public StubVendorServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the stub vendor server", ex);
        }
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            Response response = scripted.isEmpty() ? lastServed : scripted.removeFirst();
            lastServed = response;

            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            // A 204 must not declare a length; everything else here does.
            exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int requestCount() {
        return requests.get();
    }

    public StubVendorServer willAnswer(int status, String body) {
        scripted.add(new Response(status, body, Map.of()));
        return this;
    }

    public StubVendorServer willAnswer(int status, String body, Map<String, String> headers) {
        scripted.add(new Response(status, body, headers));
        return this;
    }

    /** Forgets the script and the count, for a server shared across a context's tests. */
    public void reset() {
        scripted.clear();
        lastServed = null;
        requests.set(0);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record Response(int status, String body, Map<String, String> headers) {
    }
}
