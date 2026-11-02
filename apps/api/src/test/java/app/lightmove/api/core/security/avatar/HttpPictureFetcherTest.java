package app.lightmove.api.core.security.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.http.service.PublicAddressResolver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The picture transport against a real TLS server on this machine. Its address guard would refuse that server, so the
 * transport cases run with the system resolver and the guard has a case of its own.
 */
class HttpPictureFetcherTest {

    private static final String PASSWORD = "changeit";
    private static final byte[] PICTURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @TempDir static Path keys;
    static HttpsServer server;
    static SSLContext clientTls;
    static HttpPictureFetcher fetcher;

    @BeforeAll
    static void startServer() throws Exception {
        Path keystore = keys.resolve("server.p12");
        Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-keystore", keystore.toString(), "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD).inheritIO().start();
        assertThat(keytool.waitFor(60, TimeUnit.SECONDS) && keytool.exitValue() == 0).isTrue();

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(keystore.toFile())) {
            store.load(in, PASSWORD.toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(store, PASSWORD.toCharArray());
        SSLContext serverTls = SSLContext.getInstance("TLS");
        serverTls.init(keyManagers.getKeyManagers(), null, null);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        clientTls = SSLContext.getInstance("TLS");
        clientTls.init(null, trust.getTrustManagers(), null);

        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        server.createContext("/photo", exchange -> send(exchange, 200, PICTURE, true));
        server.createContext("/expired", exchange -> send(exchange, 403, new byte[0], true));
        server.createContext("/missing", exchange -> send(exchange, 404, new byte[0], true));
        server.createContext("/down", exchange -> send(exchange, 503, new byte[0], true));
        server.createContext("/busy", exchange -> send(exchange, 429, new byte[0], true));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "https://localhost/photo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/big", exchange -> send(exchange, 200, new byte[HttpPictureFetcher.MAX_BYTES + 1], true));
        server.createContext("/endless", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[8192];
            try (OutputStream out = exchange.getResponseBody()) {
                while (true) {
                    out.write(chunk);
                    out.flush();
                }
            } catch (IOException clientGone) {
                // The fetcher hung up once past the cap, which is the point.
            }
        });
        server.createContext("/stall", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            send(exchange, 200, PICTURE, true);
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        fetcher = new HttpPictureFetcher(SystemDefaultDnsResolver.INSTANCE, clientTls,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @AfterAll
    static void stopServer() throws IOException {
        fetcher.destroy();
        server.stop(0);
    }

    @Test
    @DisplayName("a picture is fetched whole")
    void fetches() {
        assertThat(fetcher.fetch(url("/photo"))).isInstanceOfSatisfying(PictureFetch.Fetched.class,
                fetched -> assertThat(fetched.content()).isEqualTo(PICTURE));
    }

    @Test
    @DisplayName("a refusal, a missing picture and a redirect will not answer again; nothing is followed")
    void goneForGood() {
        assertThat(fetcher.fetch(url("/expired"))).isInstanceOf(PictureFetch.Gone.class);
        assertThat(fetcher.fetch(url("/missing"))).isInstanceOf(PictureFetch.Gone.class);
        assertThat(fetcher.fetch(url("/redirect"))).isInstanceOf(PictureFetch.Gone.class);
    }

    @Test
    @DisplayName("the provider's own trouble, and a timeout, might pass")
    void mightAnswerLater() {
        assertThat(fetcher.fetch(url("/down"))).isInstanceOf(PictureFetch.Unavailable.class);
        assertThat(fetcher.fetch(url("/busy"))).isInstanceOf(PictureFetch.Unavailable.class);
        assertThat(fetcher.fetch(url("/stall"))).isInstanceOf(PictureFetch.Unavailable.class);
    }

    @Test
    @DisplayName("past the cap, declared or streamed, the body is refused rather than read to the end")
    void refusesTooMuch() {
        assertThat(fetcher.fetch(url("/big"))).isInstanceOf(PictureFetch.Gone.class);
        assertThat(fetcher.fetch(url("/endless"))).isInstanceOf(PictureFetch.Gone.class);
    }

    @Test
    @DisplayName("only https is asked for")
    void httpsOnly() {
        assertThat(fetcher.fetch(URI.create("http://localhost:" + server.getAddress().getPort() + "/photo")))
                .isInstanceOf(PictureFetch.Gone.class);
    }

    @Test
    @DisplayName("a host on this machine or a private network is never dialled")
    void refusesPrivateAddresses() throws IOException {
        HttpPictureFetcher guarded = new HttpPictureFetcher(new PublicAddressResolver(), clientTls,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        try {
            assertThat(guarded.fetch(url("/photo"))).isInstanceOf(PictureFetch.Gone.class);
        } finally {
            guarded.destroy();
        }
    }

    private static URI url(String path) {
        return URI.create("https://localhost:" + server.getAddress().getPort() + path);
    }

    private static void send(HttpExchange exchange, int status, byte[] body, boolean withLength) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : withLength ? body.length : 0);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
