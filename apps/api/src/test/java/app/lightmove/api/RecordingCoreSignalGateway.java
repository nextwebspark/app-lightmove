package app.lightmove.api;

import app.lightmove.api.company.model.CoreSignalCompanyRecord;
import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import app.lightmove.api.company.model.CoreSignalSearchResult;
import app.lightmove.api.company.service.CoreSignalGateway;
import app.lightmove.api.company.service.CoreSignalUnavailableException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link CoreSignalGateway} that answers from programmed fixtures and remembers every call — the
 * {@code RecordingEmailSender} of the sourcing flow. What it records is the money: a test asserts
 * the credit safeguard by asserting {@link #collectedIds()} did not grow.
 */
public class RecordingCoreSignalGateway implements CoreSignalGateway {

    private volatile CoreSignalSearchResult searchResult = new CoreSignalSearchResult(List.of(), 0);
    private volatile CoreSignalUnavailableException searchFailure;
    private final Map<Long, CoreSignalCompanyRecord> companies = new ConcurrentHashMap<>();
    private final Map<Long, CoreSignalUnavailableException> collectFailures = new ConcurrentHashMap<>();
    private final AtomicInteger searchCalls = new AtomicInteger();
    private final List<Long> collectedIds = new CopyOnWriteArrayList<>();

    @Override
    public CoreSignalSearchResult searchCompanyIds(CoreSignalSearchCriteria criteria, int limit) {
        searchCalls.incrementAndGet();
        if (searchFailure != null) {
            throw searchFailure;
        }
        List<Long> ids = searchResult.ids();
        return new CoreSignalSearchResult(
                ids.size() > limit ? ids.subList(0, limit) : ids, searchResult.totalMatched());
    }

    @Override
    public Optional<CoreSignalCompanyRecord> collect(long coresignalId) {
        collectedIds.add(coresignalId);
        CoreSignalUnavailableException failure = collectFailures.get(coresignalId);
        if (failure != null) {
            throw failure;
        }
        return Optional.ofNullable(companies.get(coresignalId));
    }

    // ── programming ──────────────────────────────────────────────────────────

    public void givenSearch(List<Long> ids, long totalMatched) {
        this.searchResult = new CoreSignalSearchResult(List.copyOf(ids), totalMatched);
    }

    public void givenCompany(CoreSignalCompanyRecord record) {
        companies.put(record.coresignalId(), record);
    }

    public void failSearch(String detail) {
        this.searchFailure = new CoreSignalUnavailableException(detail, true);
    }

    public void searchSucceedsAgain() {
        this.searchFailure = null;
    }

    public void failCollect(long coresignalId, boolean fatal, String detail) {
        collectFailures.put(coresignalId, new CoreSignalUnavailableException(detail, fatal));
    }

    /** A minimal but presentable record — enough for cards, tiers and the drawer fields under test. */
    public static CoreSignalCompanyRecord company(long id, String name, String industry, long revenueUsd) {
        return new CoreSignalCompanyRecord(id, name, "https://" + id + ".example",
                "https://linkedin.com/company/co-" + id, "About " + name, industry,
                120, "51-200", BigDecimal.valueOf(revenueUsd), "10M-25M",
                "Dubai, United Arab Emirates", "United Arab Emirates", "AE", 2009,
                "https://logo.example/" + id + ".png", "{\"company_name\":\"" + name + "\"}");
    }

    // ── recordings ───────────────────────────────────────────────────────────

    public int searchCalls() {
        return searchCalls.get();
    }

    public List<Long> collectedIds() {
        return List.copyOf(collectedIds);
    }

    public void reset() {
        searchResult = new CoreSignalSearchResult(List.of(), 0);
        searchFailure = null;
        companies.clear();
        collectFailures.clear();
        searchCalls.set(0);
        collectedIds.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the unconfigured (keyless) gateway the app would wire. */
        @Bean
        @Primary
        public RecordingCoreSignalGateway recordingCoreSignalGateway() {
            return new RecordingCoreSignalGateway();
        }
    }
}
