package app.lightmove.api.assistant.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.utils.MarkdownParser;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * The assistant's playbooks ({@code assistant/skills/<name>/SKILL.md}), offered to the model as one
 * {@code Skill} tool that returns a playbook's text by name. Read once at boot; a malformed playbook
 * fails the start rather than being offered half-written.
 *
 * <p>Each is registered by its text alone, never by its directory: the library would otherwise tell the
 * model where the playbook sits on disk and invite it to read the files beside it.
 */
@Component
public class AssistantSkills {

    public static final String TOOL_NAME = "Skill";

    static final String LOCATION = "classpath*:assistant/skills/*/SKILL.md";

    /** The library's own limit for what a model reads when choosing. */
    static final int MAX_DESCRIPTION = 1024;

    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private static final String TOOL_DESCRIPTION = """
            Load the playbook for the consultant's question, by one skill's name as the command, and \
            follow the instructions it returns. Load it before any other tool.

            <available_skills>
            %s
            </available_skills>
            """;

    private final ToolCallback tool;
    private final List<String> names;

    public AssistantSkills() {
        this(playbooksAt(LOCATION));
    }

    AssistantSkills(List<String> playbooks) {
        SkillsTool.Builder builder = SkillsTool.builder().toolDescriptionTemplate(TOOL_DESCRIPTION);
        Set<String> seen = new LinkedHashSet<>();
        for (String playbook : playbooks) {
            MarkdownParser parsed = new MarkdownParser(playbook);
            Map<String, Object> frontMatter = parsed.getFrontMatter();
            String name = String.valueOf(frontMatter.getOrDefault("name", ""));
            String description = String.valueOf(frontMatter.getOrDefault("description", ""));
            if (!NAME.matcher(name).matches()) {
                throw new IllegalStateException("An assistant skill needs a lower-case, hyphenated name: " + name);
            }
            if (description.isBlank() || description.length() > MAX_DESCRIPTION) {
                throw new IllegalStateException("Assistant skill " + name + " needs a description of at most "
                        + MAX_DESCRIPTION + " characters");
            }
            if (parsed.getContent().isBlank()) {
                throw new IllegalStateException("Assistant skill " + name + " has no instructions");
            }
            if (!seen.add(name)) {
                throw new IllegalStateException("Two assistant skills are named " + name);
            }
            builder.addSkill(name, description, parsed.getContent());
        }
        if (seen.isEmpty()) {
            throw new IllegalStateException("No assistant skills at " + LOCATION);
        }
        this.tool = builder.build();
        this.names = List.copyOf(seen);
    }

    public ToolCallback tool() {
        return tool;
    }

    public List<String> names() {
        return names;
    }

    private static List<String> playbooksAt(String location) {
        try {
            List<String> playbooks = new ArrayList<>();
            for (Resource file : new PathMatchingResourcePatternResolver().getResources(location)) {
                try (InputStream in = file.getInputStream()) {
                    playbooks.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return playbooks;
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Assistant skills at " + location + " could not be read", unreadable);
        }
    }
}
