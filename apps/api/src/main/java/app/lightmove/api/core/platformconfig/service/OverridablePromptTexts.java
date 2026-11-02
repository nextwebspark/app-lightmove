package app.lightmove.api.core.platformconfig.service;

import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link PromptTexts} as the running app answers it: the snapshot's active override, else the shipped file. */
@Component
@RequiredArgsConstructor
public class OverridablePromptTexts implements PromptTexts {

    private final PlatformOverrides overrides;
    private final ShippedPrompts shipped;

    @Override
    public String systemText(String promptId) {
        return PlatformPrompt.byId(promptId)
                .flatMap(prompt -> Optional.ofNullable(overrides.current().prompts().get(prompt)))
                .orElseGet(() -> shipped.systemTextOf(promptId));
    }
}
