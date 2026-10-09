package app.lightmove.api.assistant.config;

import app.lightmove.api.assistant.service.AssistantSkills;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reads the assistant's playbooks off the classpath once, at boot. */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class AssistantSkillsConfig {

    @Bean
    public AssistantSkills assistantSkills() {
        AssistantSkills skills = AssistantSkills.fromClasspath();
        log.info("Assistant playbooks: {}", skills.names());
        return skills;
    }
}
