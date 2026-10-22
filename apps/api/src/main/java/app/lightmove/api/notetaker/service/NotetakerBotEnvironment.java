package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a bot is started with, the same for every runner. The run token is in it unless the bot exchanges its own
 * identity for it (#956), so a runner hands it to the bot and to nothing that logs.
 */
public final class NotetakerBotEnvironment {

    private NotetakerBotEnvironment() {
    }

    public static Map<String, String> of(NotetakerLaunch launch, LightMoveProperties properties) {
        return of(launch, properties, false);
    }

    /**
     * {@code exchangesToken}: the bot takes its token from the API with its own ID token, so none is in its
     * environment — on Cloud Run anyone who may read the execution could read it there.
     */
    public static Map<String, String> of(NotetakerLaunch launch, LightMoveProperties properties,
                                         boolean exchangesToken) {
        NotetakerSettings settings = properties.notetaker();
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("NOTETAKER_RUN_ID", launch.recordingId().toString());
        if (exchangesToken) {
            environment.put("NOTETAKER_TOKEN_AUDIENCE", tokenAudienceOf(properties));
        } else {
            environment.put("NOTETAKER_RUN_TOKEN", launch.runToken());
        }
        environment.put("NOTETAKER_JOIN_URL", launch.joinUrl());
        environment.put("NOTETAKER_PLATFORM", launch.platform().name());
        environment.put("NOTETAKER_BOT_NAME", launch.botName());
        environment.put("NOTETAKER_API_BASE_URL", apiBaseUrlOf(properties));
        environment.put("NOTETAKER_ENDS_AT", launch.endsAt().toString());
        environment.put("NOTETAKER_LOBBY_TIMEOUT_MS", String.valueOf(settings.lobbyTimeout().toMillis()));
        environment.put("NOTETAKER_MAX_DURATION_MS", String.valueOf(settings.maxDuration().toMillis()));
        return environment;
    }

    /** Whether a bot is started without its token, to exchange its own identity for it. */
    public static boolean exchangesToken(LightMoveProperties properties) {
        String account = properties.notetaker().botServiceAccount();
        return account != null && !account.isBlank();
    }

    public static String tokenAudienceOf(LightMoveProperties properties) {
        String configured = properties.notetaker().botTokenAudience();
        return configured == null || configured.isBlank() ? apiBaseUrlOf(properties) : configured.strip();
    }

    private static String apiBaseUrlOf(LightMoveProperties properties) {
        String configured = properties.notetaker().apiBaseUrl();
        String base = configured == null || configured.isBlank() ? properties.web().baseUrl() : configured;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
