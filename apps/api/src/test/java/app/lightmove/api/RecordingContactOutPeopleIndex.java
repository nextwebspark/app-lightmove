package app.lightmove.api;

import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleIndex;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutSearchAnswer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link ContactOutPeopleIndex} that answers whatever the test scripted and remembers every body it was
 * sent — the only way to prove a page is never bought twice is to count the searches that were not made.
 */
public class RecordingContactOutPeopleIndex implements ContactOutPeopleIndex {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<Map<String, Object>> counted = new CopyOnWriteArrayList<>();
    private final List<SearchAsked> searched = new CopyOnWriteArrayList<>();
    private volatile ContactOutCount count = ContactOutCount.NONE;
    private volatile String answer = "{\"metadata\":{\"page\":1,\"page_size\":25,\"total_results\":0},\"profiles\":[]}";
    private volatile RuntimeException failure;
    private volatile boolean offered = true;

    @Override
    public boolean isOffered() {
        return offered;
    }

    @Override
    public ContactOutCount count(Map<String, Object> filter) {
        counted.add(Map.copyOf(filter));
        if (failure != null) {
            throw failure;
        }
        return count;
    }

    @Override
    public ContactOutSearchAnswer search(Map<String, Object> filter, int page, int pageSize) {
        searched.add(new SearchAsked(Map.copyOf(filter), page));
        if (failure != null) {
            throw failure;
        }
        return JSON.readValue(answer, ContactOutSearchAnswer.class);
    }

    public void countAnswers(ContactOutCount scripted) {
        this.count = scripted;
    }

    /** A People Search response body, as ContactOut would send it. */
    public void searchAnswers(String responseJson) {
        this.answer = responseJson;
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void offer(boolean isOffered) {
        this.offered = isOffered;
    }

    public List<Map<String, Object>> counts() {
        return List.copyOf(counted);
    }

    public List<SearchAsked> searches() {
        return List.copyOf(searched);
    }

    public void clear() {
        counted.clear();
        searched.clear();
        count = ContactOutCount.NONE;
        answer = "{\"metadata\":{\"page\":1,\"page_size\":25,\"total_results\":0},\"profiles\":[]}";
        failure = null;
        offered = true;
    }

    public record SearchAsked(Map<String, Object> body, int page) {}

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the unconfigured stand-in a keyless test deployment gets. */
        @Bean
        @Primary
        public RecordingContactOutPeopleIndex recordingContactOutPeopleIndex() {
            return new RecordingContactOutPeopleIndex();
        }
    }
}
