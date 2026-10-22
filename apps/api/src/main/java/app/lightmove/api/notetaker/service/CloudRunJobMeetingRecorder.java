package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Starts one execution of the bot's Cloud Run Job per call ({@code jobs.run} with the run's environment as a
 * container override), as the API's own service account, which needs {@code run.jobs.runWithOverrides} on that job.
 * Never retried: a second execution is a second bot in an executive's call.
 */
@Slf4j
public class CloudRunJobMeetingRecorder implements MeetingRecorder {

    /** Past the call's own cap: the lobby wait and the upload at the end come on top of it. */
    static final Duration TASK_HEADROOM = Duration.ofMinutes(20);
    /** The scheduler's pass waits on this call; a hung one would hold every other call's bot. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final LightMoveProperties properties;
    private final GoogleAccessTokens tokens;
    private final RestClient client;
    private final String job;

    public CloudRunJobMeetingRecorder(LightMoveProperties properties, GoogleAccessTokens tokens,
                                      RestClient.Builder builder, String baseUrl) {
        this.properties = properties;
        this.tokens = tokens;
        this.job = properties.notetaker().cloudRunJob();
        if (job == null || !job.matches("projects/[^/]+/locations/[^/]+/jobs/[^/]+")) {
            throw new IllegalStateException(
                    "lightmove.notetaker.cloud-run-job must be projects/<project>/locations/<region>/jobs/<job>");
        }
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requests.setReadTimeout(READ_TIMEOUT);
        this.client = builder.baseUrl(baseUrl).requestFactory(requests).build();
    }

    @Override
    public String launch(NotetakerLaunch launch) {
        List<Map<String, String>> environment = NotetakerBotEnvironment.of(launch, properties,
                        NotetakerBotEnvironment.exchangesToken(properties)).entrySet().stream()
                .map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue()))
                .toList();
        Duration timeout = properties.notetaker().lobbyTimeout().plus(properties.notetaker().maxDuration())
                .plus(TASK_HEADROOM);
        JsonNode operation = client.post()
                .uri("/v2/" + job + ":run")
                .header("Authorization", "Bearer " + tokens.currentToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("overrides", Map.of(
                        "containerOverrides", List.of(Map.of("env", environment)),
                        "taskCount", 1,
                        "timeout", timeout.toSeconds() + "s")))
                .retrieve()
                .body(JsonNode.class);
        String execution = operation == null ? null : operation.path("metadata").path("name").asText(null);
        String operationName = operation == null ? null : operation.path("name").asText(null);
        log.info("Notetaker run {} started as {}", launch.recordingId(), execution != null ? execution : operationName);
        return execution != null ? execution : operationName;
    }

    /** The bot hears it on its next heartbeat; cancelling the execution would lose what it is uploading. */
    @Override
    public void requestLeave(String externalId) {
    }
}
