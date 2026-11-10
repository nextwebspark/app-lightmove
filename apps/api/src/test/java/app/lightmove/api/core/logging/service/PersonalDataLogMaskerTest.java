package app.lightmove.api.core.logging.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.mock.env.MockEnvironment;

class PersonalDataLogMaskerTest {

    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    @DisplayName("an address, a LinkedIn profile and a dialled phone are taken out")
    void masksContacts() {
        assertThat(PersonalDataLogMasker.redact(
                "Resend refused jane.doe@firm.com; profile https://www.linkedin.com/in/jane-doe-12ab/ mobile +971 50 123 4567"))
                .isEqualTo("Resend refused [email removed]; profile [profile removed] mobile [phone removed]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Recording " + UUID + " could not be read",
            "Claimed at 1762817400000 after 45210 ms",
            "Package of AED 1,200,000 kept",
            "Bright Data people search at acme-holdings matched 12",
            "Stripe refused (request req_9X81726354)"})
    @DisplayName("ids, timestamps, money and company slugs are left as they are")
    void leavesIdsAlone(String line) {
        assertThat(PersonalDataLogMasker.redact(line)).isEqualTo(line);
    }

    @Test
    @DisplayName("the deployed encoder masks the message and the stack trace, and leaves the MDC's ids alone")
    void encoderMasksMessageAndStackTrace() {
        LoggerContext context = new LoggerContext();
        LoggingEventCompositeJsonEncoder encoder = deployedLikeEncoder(context);
        LoggingEvent event = new LoggingEvent(getClass().getName(), context.getLogger("test"), Level.ERROR,
                "Failed to send to {} for recording {}",
                new IllegalStateException("Mail API said: no mailbox for jane.doe@firm.com"),
                new Object[] {"sara@firm.com", UUID});
        event.setMDCPropertyMap(Map.of("correlationId", "446655440000"));

        String line = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(line).doesNotContain("jane.doe@firm.com").doesNotContain("sara@firm.com")
                .contains("Failed to send to [email removed] for recording " + UUID)
                .contains("no mailbox for [email removed]")
                .contains("\"correlationId\":\"446655440000\"");
    }

    @Test
    @DisplayName("logback-spring.xml, read with no profile as deployed, masks through its JSON appender")
    void deployedConfigurationMasks() throws Exception {
        LoggerContext context = new LoggerContext();
        JoranConfigurator configurator = springBootConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(Path.of("src", "main", "resources", "logback-spring.xml").toUri().toURL());
        @SuppressWarnings("unchecked")
        ConsoleAppender<ILoggingEvent> json =
                (ConsoleAppender<ILoggingEvent>) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("JSON");
        LoggingEvent event = new LoggingEvent(getClass().getName(), context.getLogger("test"), Level.WARN,
                "Mail API refused {}", null, new Object[] {"jane.doe@firm.com"});

        String line = new String(json.getEncoder().encode(event), StandardCharsets.UTF_8);

        assertThat(line).contains("Mail API refused [email removed]").doesNotContain("jane.doe@firm.com");
    }

    /** Boot's own configurator, which reads {@code <springProfile>}; package-private, so reached by reflection. */
    private static JoranConfigurator springBootConfigurator() throws ReflectiveOperationException {
        Constructor<?> constructor = Class.forName("org.springframework.boot.logging.logback.SpringBootJoranConfigurator")
                .getDeclaredConstructor(LoggingInitializationContext.class);
        constructor.setAccessible(true);
        return (JoranConfigurator) constructor.newInstance(new LoggingInitializationContext(new MockEnvironment()));
    }

    private static LoggingEventCompositeJsonEncoder deployedLikeEncoder(LoggerContext context) {
        LoggingEventJsonProviders providers = new LoggingEventJsonProviders();
        LoggingEventPatternJsonProvider pattern = new LoggingEventPatternJsonProvider();
        pattern.setPattern("{\"correlationId\": \"%mdc{correlationId}\"}");
        providers.addPattern(pattern);
        providers.addMessage(new MessageJsonProvider());
        providers.addStackTrace(new StackTraceJsonProvider());

        MaskingJsonGeneratorDecorator masking = new MaskingJsonGeneratorDecorator();
        masking.addValueMasker(new PersonalDataLogMasker());
        masking.start();

        LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
        encoder.setContext(context);
        encoder.setProviders(providers);
        encoder.setJsonGeneratorDecorator(masking);
        encoder.start();
        return encoder;
    }
}
