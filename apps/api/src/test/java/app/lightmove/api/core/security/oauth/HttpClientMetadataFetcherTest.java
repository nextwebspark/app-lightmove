package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.McpSettings;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The metadata document transport against a real TLS server on this machine. Its own address guard would refuse that
 * server, so the transport cases run with the system resolver and the guard is tested apart — against the same live
 * server, among others.
 */
class HttpClientMetadataFetcherTest {

    private static final String PASSWORD = "changeit";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final McpSettings SETTINGS = new McpSettings("", "file:unused", "file:unused",
            Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(5), 30, 60, 30, 10, 30, Duration.ofDays(30),
            5120, TIMEOUT, Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofHours(24),
            List.of("https://claude.ai/oauth/"), List.of(), 65536, 120, 30);
    private static final String DOCUMENT = "{\"client_id\":\"x\"}";

    @TempDir static Path keys;
    static HttpsServer server;
    static SSLContext clientTls;
    static HttpClientMetadataFetcher fetcher;

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
        server.createContext("/doc", exchange -> {
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=600");
            send(exchange, 200, "application/json", DOCUMENT.getBytes(StandardCharsets.UTF_8), true);
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "https://localhost/doc");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/big", exchange ->
                send(exchange, 200, "application/json", new byte[6000], true));
        server.createContext("/chunked-big", exchange ->
                send(exchange, 200, "application/json", new byte[6000], false));
        server.createContext("/endless", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[1024];
            try (OutputStream out = exchange.getResponseBody()) {
                while (true) {
                    out.write(chunk);
                    out.flush();
                }
            } catch (IOException clientGone) {
                // The fetcher hung up once past the cap, which is the point.
            }
        });
        server.createContext("/text", exchange ->
                send(exchange, 200, "text/html", DOCUMENT.getBytes(StandardCharsets.UTF_8), true));
        server.createContext("/error", exchange ->
                send(exchange, 503, "application/json", DOCUMENT.getBytes(StandardCharsets.UTF_8), true));
        server.createContext("/stall", exchange -> {
            sleep(3000);
            send(exchange, 200, "application/json", DOCUMENT.getBytes(StandardCharsets.UTF_8), true);
        });
        server.createContext("/drip", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 40; i++) {
                    out.write(' ');
                    out.flush();
                    sleep(200);
                }
            } catch (IOException clientGone) {
                // The fetcher hung up at its deadline, which is the point.
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        fetcher = new HttpClientMetadataFetcher(SETTINGS, SystemDefaultDnsResolver.INSTANCE, clientTls);
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stop(0);
        fetcher.destroy();
    }

    @Test
    @DisplayName("a document is read with the lifetime its host gave it")
    void fetchesDocument() {
        FetchedClientMetadata fetched = fetcher.fetch(at("/doc"));
        assertThat(fetched.body()).isEqualTo(DOCUMENT);
        assertThat(fetched.lifetime()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("a redirect is refused, not followed")
    void redirectRefused() {
        assertRefused(at("/redirect"), ClientMetadataRefusal.REDIRECT);
    }

    @Test
    @DisplayName("a body past the cap is refused, whether its length is declared or it is sent chunked")
    void oversizedRefused() {
        assertRefused(at("/big"), ClientMetadataRefusal.TOO_LARGE);
        assertRefused(at("/chunked-big"), ClientMetadataRefusal.TOO_LARGE);
    }

    @Test
    @DisplayName("an endless body is refused as too large at once, not drained until the deadline")
    void endlessBodyRefusedAsTooLarge() {
        long started = System.nanoTime();
        assertRefused(at("/endless"), ClientMetadataRefusal.TOO_LARGE);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(TIMEOUT);
    }

    @Test
    @DisplayName("a host that stalls, or trickles bytes, is cut off at the deadline")
    void slowRefused() {
        long started = System.nanoTime();
        assertRefused(at("/stall"), ClientMetadataRefusal.TIMEOUT);
        assertRefused(at("/drip"), ClientMetadataRefusal.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("anything but JSON, an error status and plain http are refused")
    void otherRefusals() {
        assertRefused(at("/text"), ClientMetadataRefusal.NOT_JSON);
        assertRefused(at("/error"), ClientMetadataRefusal.HTTP_ERROR);
        assertRefused(URI.create("http://localhost:" + server.getAddress().getPort() + "/doc"),
                ClientMetadataRefusal.NOT_HTTPS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "[::1]", "10.0.0.1", "169.254.169.254", "[::ffff:127.0.0.1]",
            "192.168.1.10", "172.16.0.1", "100.64.0.1", "[fc00::1]", "[fe80::1]", "[64:ff9b::7f00:1]", "0.0.0.0"})
    @DisplayName("the real guard refuses a host on this machine or a private network, a live server here included")
    void privateAddressesRefused(String host) throws Exception {
        HttpClientMetadataFetcher guarded = new HttpClientMetadataFetcher(SETTINGS, new PublicAddressResolver(),
                clientTls);
        try {
            assertThatThrownBy(() -> guarded.fetch(
                    URI.create("https://" + host + ":" + server.getAddress().getPort() + "/doc")))
                    .isInstanceOfSatisfying(ClientMetadataUnavailable.class,
                            refused -> assertThat(refused.refusal()).isEqualTo(ClientMetadataRefusal.PRIVATE_ADDRESS));
        } finally {
            guarded.destroy();
        }
    }

    @Test
    @DisplayName("public addresses pass the guard")
    void publicAddressesPass() throws Exception {
        for (String address : new String[] {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "64:ff9b::808:808"}) {
            assertThat(PublicAddressResolver.isPublic(InetAddress.getByName(address))).as(address).isTrue();
        }
    }

    @Test
    @DisplayName("a lifetime is held between the floor and the ceiling; no-store and silence get the floor")
    void lifetimeClamped() {
        assertThat(fetcher.lifetimeOf("max-age=30")).isEqualTo(Duration.ofMinutes(5));
        assertThat(fetcher.lifetimeOf("max-age=3600")).isEqualTo(Duration.ofHours(1));
        assertThat(fetcher.lifetimeOf("max-age=31536000")).isEqualTo(Duration.ofHours(24));
        assertThat(fetcher.lifetimeOf("no-store, max-age=3600")).isEqualTo(Duration.ofMinutes(5));
        assertThat(fetcher.lifetimeOf(null)).isEqualTo(Duration.ofMinutes(5));
        assertThat(fetcher.lifetimeOf("max-age=99999999999999999999")).isEqualTo(Duration.ofHours(24));
    }

    private static URI at(String path) {
        return URI.create("https://localhost:" + server.getAddress().getPort() + path);
    }

    private static void assertRefused(URI url, ClientMetadataRefusal expected) {
        assertThatThrownBy(() -> fetcher.fetch(url)).isInstanceOfSatisfying(ClientMetadataUnavailable.class,
                refused -> assertThat(refused.refusal()).as(url.toString()).isEqualTo(expected));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body, boolean declareLength)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, declareLength ? body.length : 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        } catch (IOException clientGone) {
            // A refusal can hang up before the body is written.
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
