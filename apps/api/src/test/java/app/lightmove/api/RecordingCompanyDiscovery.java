package app.lightmove.api;

import app.lightmove.api.companydiscovery.constant.DiscoveryMode;
import app.lightmove.api.companydiscovery.model.DiscoveredCandidate;
import app.lightmove.api.companydiscovery.model.DiscoveryAnswer;
import app.lightmove.api.companydiscovery.model.DiscoveryQuery;
import app.lightmove.api.companydiscovery.service.CompanyDiscovery;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link CompanyDiscovery} that answers whatever the test scripted and remembers what it was
 * asked. The twin of {@link RecordingCompanyEnricher}, and the reason no suite needs Vertex to
 * exercise resolution, the spend cap or the grid.
 */
public class RecordingCompanyDiscovery implements CompanyDiscovery {

    private final List<DiscoveryQuery> asked = new CopyOnWriteArrayList<>();
    private volatile List<DiscoveredCandidate> answer = List.of();
    private volatile DiscoveryMode mode = DiscoveryMode.GROUNDED_STRUCTURED;
    private volatile boolean enabled = true;

    @Override
    public DiscoveryAnswer discover(DiscoveryQuery query) {
        asked.add(query);
        return new DiscoveryAnswer(List.copyOf(answer), mode);
    }

    @Override
    public String provider() {
        return "recording";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void answerWith(DiscoveredCandidate... candidates) {
        this.answer = List.of(candidates);
    }

    public void answerIn(DiscoveryMode answeredMode) {
        this.mode = answeredMode;
    }

    public void offer(boolean offered) {
        this.enabled = offered;
    }

    public void clear() {
        asked.clear();
        answer = List.of();
        mode = DiscoveryMode.GROUNDED_STRUCTURED;
        enabled = true;
    }

    public List<DiscoveryQuery> questionsAsked() {
        return List.copyOf(asked);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingCompanyDiscovery recordingCompanyDiscovery() {
            return new RecordingCompanyDiscovery();
        }
    }
}
