package app.lightmove.api;

import app.lightmove.api.core.storage.model.RemoteImage;
import app.lightmove.api.core.storage.service.RemoteImageFetcher;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Answers the pictures a test scripted, and remembers every address it was asked for; no test reaches another host. */
public class RecordingRemoteImageFetcher implements RemoteImageFetcher {

    private final Map<String, RemoteImage> scripted = new ConcurrentHashMap<>();
    private final List<URI> asked = new CopyOnWriteArrayList<>();

    @Override
    public Optional<RemoteImage> fetch(URI address) {
        asked.add(address);
        return Optional.ofNullable(scripted.get(address.toString()));
    }

    public void answer(String address, byte[] content, String contentType) {
        scripted.put(address, new RemoteImage(content, contentType));
    }

    public long timesAsked(String address) {
        return asked.stream().filter(uri -> uri.toString().equals(address)).count();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingRemoteImageFetcher recordingRemoteImageFetcher() {
            return new RecordingRemoteImageFetcher();
        }
    }
}
