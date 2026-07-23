package app.lightmove.api;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.BriefExtraction;
import app.lightmove.api.core.llm.service.BriefLlmClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link BriefLlmClient} a test can script — no Vertex AI account, no network, deterministic
 * output. Mirrors {@link RecordingEmailSender}: {@code @Primary} so it wins over the LogBriefLlmClient
 * the application would otherwise pick.
 */
public class FakeBriefLlmClient implements BriefLlmClient {

    private volatile BriefExtraction next = BriefExtraction.empty();
    private volatile boolean failNext = false;

    /** The extraction the next call returns. Stays in effect until changed again. */
    public void respondWith(BriefExtraction extraction) {
        this.next = extraction;
        this.failNext = false;
    }

    /** The next call throws {@code BRIEF_EXTRACTION_FAILED}, as a real provider outage would. */
    public void failNext() {
        this.failNext = true;
    }

    @Override
    public BriefExtraction extractBrief(String documentText, String positionTitle) {
        if (failNext) {
            failNext = false;
            throw ApiException.of(ErrorCode.BRIEF_EXTRACTION_FAILED);
        }
        return next;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public FakeBriefLlmClient fakeBriefLlmClient() {
            return new FakeBriefLlmClient();
        }
    }
}
