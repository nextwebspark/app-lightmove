package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.BriefExtraction;
import lombok.extern.slf4j.Slf4j;

/**
 * The default: logs what it would have sent and reports nothing found. Mirrors {@code LogEmailSender}
 * — a fresh clone and the test suite exercise the whole upload → extraction → apply pipeline with no
 * GCP AI account, at the cost of every field simply staying whatever it already was.
 */
@Slf4j
public class LogBriefLlmClient implements BriefLlmClient {

    @Override
    public BriefExtraction extractBrief(String documentText, String positionTitle) {
        log.info("LLM provider is 'log' — would extract a brief for '{}' from {} characters of "
                + "document text; reporting nothing found.", positionTitle, documentText.length());
        return BriefExtraction.empty();
    }
}
