package app.lightmove.api;

import app.lightmove.api.core.security.oauth.ClientMetadataFetcher;
import app.lightmove.api.core.security.oauth.ClientMetadataRefusal;
import app.lightmove.api.core.security.oauth.ClientMetadataUnavailable;
import app.lightmove.api.core.security.oauth.FetchedClientMetadata;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Serves client id metadata documents from memory, so an integration test never reaches the internet. A URL with no
 * document answers as an unreachable host would; the transport's own guards are {@code HttpClientMetadataFetcherTest}'s.
 */
public class StubClientMetadataFetcher implements ClientMetadataFetcher {

    private final Map<String, FetchedClientMetadata> documents = new ConcurrentHashMap<>();
    private final List<String> fetched = new CopyOnWriteArrayList<>();

    public void serve(String url, String body, Duration lifetime) {
        documents.put(url, new FetchedClientMetadata(body, lifetime));
    }

    public void takeDown(String url) {
        documents.remove(url);
    }

    public List<String> fetched() {
        return List.copyOf(fetched);
    }

    @Override
    public FetchedClientMetadata fetch(URI documentUrl) {
        fetched.add(documentUrl.toString());
        FetchedClientMetadata document = documents.get(documentUrl.toString());
        if (document == null) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.UNREACHABLE, documentUrl.toString());
        }
        return document;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public StubClientMetadataFetcher stubClientMetadataFetcher() {
            return new StubClientMetadataFetcher();
        }
    }
}
