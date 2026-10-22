package app.lightmove.api.notetaker.config;

import app.lightmove.api.notetaker.service.MeetingRecorder;
import app.lightmove.api.notetaker.service.LogMeetingRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

/** The recorder our own bot is sent through; a test's recording one replaces it. */
@Configuration(proxyBeanMethods = false)
public class NotetakerConfig {

    @Bean
    @Fallback
    MeetingRecorder ownMeetingRecorder() {
        return new LogMeetingRecorder();
    }
}
