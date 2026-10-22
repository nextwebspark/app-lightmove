package app.lightmove.api.notetaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.service.CloudRunJobMeetingRecorder;
import app.lightmove.api.notetaker.service.LocalProcessMeetingRecorder;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NotetakerLaunchersTest {

    private static final String JOB = "projects/uncava-prod/locations/us-central1/jobs/lightmove-meetingbot";

    private final NotetakerLaunch launch = new NotetakerLaunch(UUID.randomUUID(), NotetakerPlatform.GOOGLE_MEET,
            "https://meet.google.com/abc-defg-hij", "Uncava Notetaker", "secret-run-token",
            Instant.parse("2026-10-22T11:30:00Z"));

    @Test
    void aCloudRunJobIsRunOnceWithTheRunsEnvironmentAsAnOverride() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = """
                    {"name":"projects/uncava-prod/locations/us-central1/operations/op-1",
                     "metadata":{"name":"projects/uncava-prod/locations/us-central1/jobs/lightmove-meetingbot/executions/exec-1"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        try {
            CloudRunJobMeetingRecorder recorder = new CloudRunJobMeetingRecorder(properties("cloud-run-job", JOB, null),
                    () -> "google-token", RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort());

            String externalId = recorder.launch(launch);

            assertThat(externalId).endsWith("/executions/exec-1");
            assertThat(path.get()).isEqualTo("/v2/" + JOB + ":run");
            assertThat(authorization.get()).isEqualTo("Bearer google-token");
            JsonNode overrides = JsonMapper.builder().build().readTree(body.get()).path("overrides");
            assertThat(overrides.path("taskCount").asInt()).isEqualTo(1);
            assertThat(overrides.path("timeout").asText()).isEqualTo((10 + 180 + 20) * 60 + "s");
            JsonNode environment = overrides.path("containerOverrides").get(0).path("env");
            assertThat(valueOf(environment, "NOTETAKER_RUN_TOKEN")).isEqualTo("secret-run-token");
            assertThat(valueOf(environment, "NOTETAKER_JOIN_URL")).isEqualTo("https://meet.google.com/abc-defg-hij");
            assertThat(valueOf(environment, "NOTETAKER_API_BASE_URL")).isEqualTo("https://beta.uncava.com");
            assertThat(valueOf(environment, "NOTETAKER_PLATFORM")).isEqualTo("GOOGLE_MEET");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aMisnamedJobFailsTheStartRatherThanTheFirstCall() {
        assertThatThrownBy(() -> new CloudRunJobMeetingRecorder(properties("cloud-run-job", "lightmove-meetingbot",
                null), () -> "token", RestClient.builder(), "http://127.0.0.1:1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theLocalRunnerStartsTheBotWithTheSameEnvironment(@TempDir Path root) throws Exception {
        LocalProcessMeetingRecorder recorder = new LocalProcessMeetingRecorder(
                properties("local", null, "sh -c env"), root);

        String externalId = recorder.launch(launch);

        assertThat(externalId).startsWith("local-");
        Path output = root.resolve(".data/notetaker/" + launch.recordingId() + ".log");
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)
                && (!Files.exists(output) || Files.readString(output).isBlank())) {
            Thread.onSpinWait();
        }
        String environment = Files.readString(output);
        assertThat(environment).contains("NOTETAKER_RUN_ID=" + launch.recordingId());
        // Nothing of the API's own environment but what finds node and Chromium.
        assertThat(environment.lines().map(line -> line.split("=", 2)[0]))
                .allMatch(name -> name.startsWith("NOTETAKER_") || List.of("PATH", "HOME", "CHROME_PATH", "TMPDIR",
                        "LANG", "PWD", "SHLVL", "_").contains(name));
    }

    private static String valueOf(JsonNode environment, String name) {
        for (JsonNode entry : environment) {
            if (entry.path("name").asText().equals(name)) {
                return entry.path("value").asText();
            }
        }
        return null;
    }

    private static LightMoveProperties properties(String runner, String job, String localCommand) {
        NotetakerSettings notetaker = new NotetakerSettings(true, List.of("GOOGLE_MEET"), "Uncava Notetaker",
                Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofMinutes(10), Duration.ofHours(3), "PT1M", runner, "https://beta.uncava.com/", job,
                localCommand, 8_388_608L);
        return new LightMoveProperties(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, notetaker);
    }
}
