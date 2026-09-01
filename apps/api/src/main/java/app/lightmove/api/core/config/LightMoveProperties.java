package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable the application has, in one typed tree.
 *
 * <p>Records make these immutable and fail-fast: a typo in a key surfaces at startup as a binding
 * error, not at 3am as a {@code null} in a token expiry calculation.
 *
 * <p>This is the root only. Each branch is its own {@code *Settings} record in this package, so the
 * yml keys ({@code lightmove.auth.jwt.*}, {@code lightmove.email.validation.*}, …) read straight off
 * the component names here and there.
 */
@ConfigurationProperties(prefix = "lightmove")
public record LightMoveProperties(
        AuthSettings auth,
        EmailSettings email,
        WebSettings web,
        CompanySettings company,
        PositionSettings position,
        LlmSettings llm
) {}
