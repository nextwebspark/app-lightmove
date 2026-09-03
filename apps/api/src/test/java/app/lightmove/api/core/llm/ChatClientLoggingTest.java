package app.lightmove.api.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.candidate.service.CandidateShortlistService;
import app.lightmove.api.core.llm.config.ChatClientConfig;
import app.lightmove.api.core.llm.service.ChatCallLog;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

/**
 * What the shared {@link ChatClient} writes to the log for every call.
 *
 * <p>Driven through a real {@code ChatClient} rather than by calling the formatters directly: what is
 * worth proving is that a call site's prompt id survives the trip into
 * {@code ChatClientRequest.context()}. A formatter tested in isolation would keep passing while every
 * line in production read {@code unattributed}.
 */
class ChatClientLoggingTest {

    private static final String ADVISOR_LOGGER =
            "org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor";

    private Logger advisorLogger;
    private ListAppender<ILoggingEvent> logged;
    private Level originalLevel;

    @BeforeEach
    void captureTheAdvisorsLog() {
        advisorLogger = (Logger) LoggerFactory.getLogger(ADVISOR_LOGGER);
        originalLevel = advisorLogger.getLevel();
        // The advisor logs at DEBUG. The suite runs at INFO, so without this the appender sees nothing
        // and every assertion below would pass against an empty list.
        advisorLogger.setLevel(Level.DEBUG);
        logged = new ListAppender<>();
        logged.start();
        advisorLogger.addAppender(logged);
    }

    @AfterEach
    void restoreTheLog() {
        advisorLogger.detachAppender(logged);
        advisorLogger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("logs the model, both token counts and the finish reason")
    void logsTheMetadataThatAnswersACostOrTruncationQuestion() {
        chatClientOver(new FixedChatModel()).prompt().user("anything").call().content();

        assertThat(lineContaining("chat response"))
                .contains("model=gemini-2.5-flash")
                .contains("inputTokens=1200")
                .contains("outputTokens=340")
                .contains("totalTokens=1540")
                // The field that separates a good answer from a silently truncated one: a MAX_TOKENS
                // stop returns a partial body over a perfectly successful call.
                .contains("finish=MAX_TOKENS")
                .contains("id=resp-1");
    }

    @Test
    @DisplayName("names the calling feature, so one shared client's lines can be told apart")
    void namesTheCallingFeature() {
        chatClientOver(new FixedChatModel())
                .prompt()
                .advisors(advisor -> advisor.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, "import-column-mapping"))
                .user("anything")
                .call()
                .content();

        assertThat(lineContaining("chat request")).contains("prompt=import-column-mapping");
    }

    @Test
    @DisplayName("a call site that names no feature is reported as such, never as an error")
    void toleratesAnUnattributedCall() {
        chatClientOver(new FixedChatModel()).prompt().user("anything").call().content();

        assertThat(lineContaining("chat request")).contains("prompt=unattributed");
    }

    @Test
    @DisplayName("the shortlist service tags its own calls")
    void shortlistTagsItsCalls() {
        // Through the real service, because the tag is only useful if the call sites actually set it.
        shortlistService().shortlist("a job brief", "a candidate profile");

        assertThat(lineContaining("chat request")).contains("prompt=recruiter-shortlist");
    }

    @Test
    @DisplayName("never logs the prompt or the answer, whatever they hold")
    void logsNoContent() {
        // The guarantee this whole configuration exists for: SimpleLoggerAdvisor's own default
        // formatters dump the full request and response, and a job brief, a candidate profile and a
        // spreadsheet of executives are all client and candidate PII.
        shortlistService().shortlist("CFO for Aurora Capital", "Layla Haddad, layla@acwa.example");

        assertThat(logged.list).isNotEmpty();
        assertThat(logged.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain("Layla Haddad")
                .doesNotContain("layla@acwa.example")
                .doesNotContain("Aurora Capital")
                .doesNotContain("SHORTLIST this candidate"));
    }

    @Test
    @DisplayName("a response carrying no metadata at all still logs a line")
    void survivesMissingMetadata() {
        // A failure before the model ran. A logger that throws turns one incident into two.
        chatClientOver(new EmptyMetadataChatModel()).prompt().user("anything").call().content();

        // Spring AI substitutes an empty usage rather than a null, so an unreported count arrives as
        // 0 — pinned here so the day that changes is a failing test rather than silent under-counting
        // in whatever totals these lines feed.
        assertThat(lineContaining("chat response"))
                .contains("id=?")
                .contains("model=?")
                .contains("inputTokens=0")
                .contains("finish=?");
    }

    private CandidateShortlistService shortlistService() {
        return new CandidateShortlistService(chatClientOver(new FixedChatModel()),
                new ByteArrayResource("system".getBytes()), TestLlmCallPolicy.asShipped());
    }

    private ChatClient chatClientOver(ChatModel model) {
        return new ChatClientConfig().chatClient(ChatClient.builder(model), "gemini-2.5-flash", 0.8);
    }

    private String lineContaining(String fragment) {
        return logged.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no log line containing '%s' in %s".formatted(fragment, logged.list)));
    }

    /** Answers with the metadata a real provider returns, so the formatter is exercised, not mocked. */
    private static final class FixedChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage("SHORTLIST this candidate"),
                            ChatGenerationMetadata.builder().finishReason("MAX_TOKENS").build())),
                    ChatResponseMetadata.builder()
                            .id("resp-1")
                            .model("gemini-2.5-flash")
                            .usage(new DefaultUsage(1200, 340))
                            .build());
        }
    }

    /** No usage and no finish reason — what a call that failed before the model ran looks like. */
    private static final class EmptyMetadataChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
        }
    }
}
