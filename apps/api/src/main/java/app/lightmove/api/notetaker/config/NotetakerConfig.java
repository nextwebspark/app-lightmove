package app.lightmove.api.notetaker.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.model.OfferedPlatforms;
import app.lightmove.api.notetaker.service.LogMeetingRecorder;
import app.lightmove.api.notetaker.service.MeetingRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

/** The recorder our own bot is sent through (a test's recording one replaces it), and the platforms it joins. */
@Configuration(proxyBeanMethods = false)
public class NotetakerConfig {

    @Bean
    @Fallback
    MeetingRecorder ownMeetingRecorder() {
        return new LogMeetingRecorder();
    }

    @Bean
    OfferedPlatforms offeredNotetakerPlatforms(LightMoveProperties properties) {
        return OfferedPlatforms.parse(properties.notetaker().platforms());
    }
}
