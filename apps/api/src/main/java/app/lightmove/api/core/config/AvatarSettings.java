package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Profile photos — {@code lightmove.avatars.*}. The multipart ceiling in {@code spring.servlet.multipart} must stay
 * above {@code maxUploadBytes}, or the container refuses the body before this rule can say why.
 */
public record AvatarSettings(
        @DefaultValue("5242880") long maxUploadBytes
) {

    public AvatarSettings {
        if (maxUploadBytes < 1) {
            throw new IllegalArgumentException("lightmove.avatars.max-upload-bytes must be positive");
        }
    }
}
