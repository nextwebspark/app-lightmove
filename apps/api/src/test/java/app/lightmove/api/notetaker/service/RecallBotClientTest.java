package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class RecallBotClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a transcript link is fetched only over https from Recall's own hosts or its storage")
    void onlyRecallsOwnLinksAreFetched() {
        assertThat(RecallBotClient.downloadOf("https://us-east-1.recall.ai/transcript/abc.json?sig=1")).isNotNull();
        assertThat(RecallBotClient.downloadOf("https://recall-prod.s3.amazonaws.com/t.json")).isNotNull();
        assertThat(RecallBotClient.downloadOf("http://us-east-1.recall.ai/t.json")).isNull();
        assertThat(RecallBotClient.downloadOf("https://recall.ai.evil.example/t.json")).isNull();
        assertThat(RecallBotClient.downloadOf("https://169.254.169.254/latest")).isNull();
        assertThat(RecallBotClient.downloadOf("https://user:pw@x.recall.ai/t.json")).isNull();
        assertThat(RecallBotClient.downloadOf("not a link")).isNull();
        assertThat(RecallBotClient.downloadOf(null)).isNull();
    }

    @Test
    @DisplayName("each turn is one line at its first word, named by the participant, numbered when Recall had no name")
    void turnsBecomeLines() {
        List<TranscriptLine> lines = RecallBotClient.linesOf(JSON.readTree("""
                [{"participant":{"id":7,"name":"Priya Raman"},
                  "words":[{"text":"I led","start_timestamp":{"absolute":"2026-10-22T09:00:02Z"}},
                           {"text":" the plan.","start_timestamp":{"absolute":"2026-10-22T09:00:03Z"}}]},
                 {"participant":{"id":8,"name":null},
                  "words":[{"text":"Thanks","start_timestamp":{"absolute":"2026-10-22T09:00:09Z"}}]},
                 {"participant":{"id":9,"name":"Silent"},"words":[]}]"""));

        assertThat(lines).containsExactly(
                new TranscriptLine(Instant.parse("2026-10-22T09:00:02Z"), "Priya Raman", "I led the plan.", true),
                new TranscriptLine(Instant.parse("2026-10-22T09:00:09Z"), "Speaker 8", "Thanks", false));
        assertThat(RecallBotClient.linesOf(JSON.readTree("{}"))).isEmpty();
    }

    @Test
    @DisplayName("a transcript file served as application/octet-stream, as Recall's storage serves it, is read as JSON")
    void transcriptFileServedAsOctetStream() throws IOException {
        HttpServer storage = storageServing("""
                [{"participant":{"id":7,"name":"Priya Raman"},
                  "words":[{"text":"Hello","start_timestamp":{"absolute":"2026-10-22T09:00:02Z"}}]}]""");
        try {
            URI file = URI.create("http://127.0.0.1:" + storage.getAddress().getPort() + "/transcript.json");

            assertThat(RecallBotClient.linesOf(RecallBotClient.turnsAt(RestClient.create(), file))).containsExactly(
                    new TranscriptLine(Instant.parse("2026-10-22T09:00:02Z"), "Priya Raman", "Hello", true));
        } finally {
            storage.stop(0);
        }
    }

    @Test
    @DisplayName("a transcript file that is not an array of turns is malformed, never an empty call")
    void transcriptFileNotTurnsIsMalformed() throws IOException {
        for (String body : List.of("{\"error\":\"expired\"}", "not json", "")) {
            HttpServer storage = storageServing(body);
            try {
                URI file = URI.create("http://127.0.0.1:" + storage.getAddress().getPort() + "/transcript.json");

                assertThatThrownBy(() -> RecallBotClient.turnsAt(RestClient.create(), file))
                        .isInstanceOfSatisfying(VendorException.class, failure ->
                                assertThat(failure.getKind()).isEqualTo(VendorFailureKind.MALFORMED_RESPONSE));
            } finally {
                storage.stop(0);
            }
        }
    }

    @Test
    @DisplayName("both webhook shapes name the bot and its status, and a transcript's events are told apart")
    void webhookShapes() {
        assertThat(notices("""
                {"event":"bot.status_change","data":{"bot":{"id":"b1"},
                 "status":{"code":"in_call_recording","sub_code":null,"created_at":"2026-10-22T09:00:00Z"}}}"""))
                .containsExactly(new RecallBotNotice.StatusChanged("b1", "in_call_recording", null,
                        Instant.parse("2026-10-22T09:00:00Z")));
        assertThat(notices("""
                {"event":"bot.fatal","data":{"bot":{"id":"b1"},
                 "data":{"code":"FATAL","sub_code":"meeting_not_found","updated_at":"2026-10-22T09:01:00Z"}}}"""))
                .containsExactly(new RecallBotNotice.StatusChanged("b1", "fatal", "meeting_not_found",
                        Instant.parse("2026-10-22T09:01:00Z")));
        assertThat(notices("""
                {"event":"transcript.done","data":{"bot":{"id":"b1"},"transcript":{"id":"t1"}}}"""))
                .containsExactly(new RecallBotNotice.TranscriptReady("b1", "t1", null));
        assertThat(notices("""
                {"event":"transcript.failed","data":{"bot":{"id":"b1"},"transcript":{"id":"t1"}}}"""))
                .containsExactly(new RecallBotNotice.TranscriptFailed("b1", "t1", null));
        // A transcript asked of a recording after the call names its recording, and may not name its bot.
        assertThat(notices("""
                {"event":"transcript.done","data":{"transcript":{"id":"t2"},"recording":{"id":"rec1"}}}"""))
                .containsExactly(new RecallBotNotice.TranscriptReady(null, "t2", "rec1"));
        assertThat(notices("{\"event\":\"bot.done\",\"data\":{\"data\":{\"code\":\"done\"}}}")).isEmpty();
        assertThat(notices("{\"event\":\"calendar.update\",\"data\":{\"calendar_id\":\"c1\"}}")).isEmpty();
        assertThat(notices("not json")).isEmpty();
    }

    @Test
    @DisplayName("a bot is scheduled for the call with its consent line, our ids, Recall's own transcription and leave rules")
    void requestBody() {
        Map<String, Object> body = RecallBotClient.bodyOf(new RecallBotRequest("https://meet.google.com/abc-defg-hij",
                Instant.parse("2026-10-22T08:59:00Z"), "UNCAVA Notetaker", "Hello", Map.of("recordingId", "r1"), null));

        assertThat(body).containsEntry("meeting_url", "https://meet.google.com/abc-defg-hij")
                .containsEntry("join_at", "2026-10-22T08:59:00Z")
                .containsEntry("bot_name", "UNCAVA Notetaker")
                .containsEntry("metadata", Map.of("recordingId", "r1"))
                .containsEntry("chat", Map.of("on_bot_join", Map.of("send_to", "everyone", "message", "Hello")));
        assertThat(body.get("recording_config").toString()).contains("recallai_streaming");
        assertThat(RecallBotClient.recordingConfigOf(null).get("retention"))
                .isEqualTo(Map.of("type", "timed", "hours", 168L));
        assertThat(((Map<?, ?>) body.get("recording_config")).get("retention"))
                .isEqualTo(Map.of("type", "timed", "hours", 168L));
        assertThat(body.get("automatic_leave")).isEqualTo(RecallBotClient.AUTOMATIC_LEAVE);
        assertThat(body.get("automatic_video_output")).isEqualTo(RecallBotClient.AUTOMATIC_VIDEO_OUTPUT);
        assertThat(RecallBotClient.AUTOMATIC_LEAVE).containsEntry("waiting_room_timeout", 900)
                .containsEntry("everyone_left_timeout", Map.of("timeout", 30));
        assertThat(RecallBotClient.bodyOf(new RecallBotRequest("https://zoom.us/j/1", null, "UNCAVA Notetaker",
                "Hello", Map.of(), null))).doesNotContainKey("join_at");
    }

    @Test
    @DisplayName("only a pre-signed file on Recall's or AWS's hosts is copied as a recording")
    void recordingVideoIsRecallsOwn() throws IOException {
        assertThat(RecallBotClient.downloadOf("https://bot-data.s3.amazonaws.com/v.mp4?X=1")).isNotNull();
        assertThat(RecallBotClient.downloadOf("https://evil.example/v.mp4")).isNull();
        HttpServer recall = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        recall.start();
        try {
            RecallBotClient client = clientOf(recall);

            assertThatThrownBy(() -> client.copyVideo(URI.create("https://evil.example/v.mp4"),
                    OutputStream.nullOutputStream())).isInstanceOf(IOException.class);
        } finally {
            recall.stop(0);
        }
    }

    @Test
    @DisplayName("the bot's camera is Uncava's card, a JPEG while recording and before")
    void cameraCard() {
        for (String state : List.of("in_call_recording", "in_call_not_recording")) {
            @SuppressWarnings("unchecked")
            Map<String, String> image = (Map<String, String>) RecallBotClient.AUTOMATIC_VIDEO_OUTPUT.get(state);
            assertThat(image).containsEntry("kind", "jpeg");
            byte[] jpeg = Base64.getDecoder().decode(image.get("b64_data"));
            assertThat(jpeg).startsWith((byte) 0xFF, (byte) 0xD8);
            assertThat(jpeg.length).isBetween(1_000, 1_000_000);
        }
    }

    @Test
    @DisplayName("a read of a bot answers its latest status and its recording's transcript")
    void botState() {
        RecallBotState state = RecallBotClient.stateOf(JSON.readTree("""
                {"id":"b1","status_changes":[
                   {"code":"joining_call","sub_code":null,"created_at":"2026-10-22T09:00:00Z"},
                   {"code":"done","sub_code":"call_ended_by_host","created_at":"2026-10-22T09:40:00Z"}],
                 "recordings":[{"id":"rec1","media_shortcuts":{"transcript":{"id":"t1","status":{"code":"done"}}}}]}
                """));

        assertThat(state).isEqualTo(new RecallBotState("done", "call_ended_by_host",
                Instant.parse("2026-10-22T09:40:00Z"), "t1", "done", "rec1"));
        assertThat(RecallBotClient.stateOf(JSON.readTree("{\"id\":\"b1\"}")))
                .isEqualTo(new RecallBotState(null, null, null, null, null, null));
    }

    @Test
    @DisplayName("Recall's 507 is an empty pool of bots that join at once, answered busy; any other 5xx is a failure")
    void anEmptyPoolIsBusy() throws IOException {
        HttpServer recall = recallAnswering(507);
        try {
            RecallBotRequest atOnce = new RecallBotRequest("https://meet.google.com/abc-defg-hij", null,
                    "UNCAVA Notetaker", "Hi everyone", Map.of(), null);
            assertThatThrownBy(() -> clientOf(recall).send(atOnce)).isInstanceOfSatisfying(ApiException.class,
                    refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.NOTETAKER_BUSY));
        } finally {
            recall.stop(0);
        }
        HttpServer failing = recallAnswering(503);
        try {
            assertThatThrownBy(() -> clientOf(failing).send(new RecallBotRequest("https://meet.google.com/x", null,
                    "UNCAVA Notetaker", "Hi", Map.of(), null))).isInstanceOfSatisfying(VendorException.class,
                    failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.UNAVAILABLE));
        } finally {
            failing.stop(0);
        }
    }

    @Test
    @DisplayName("a leave takes the bot out of its call, and a remove falls back to it once the bot has joined")
    void leaveAndRemove() throws IOException {
        List<String> asked = new CopyOnWriteArrayList<>();
        HttpServer recall = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        recall.createContext("/", exchange -> {
            asked.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(exchange.getRequestMethod().equals("DELETE") ? 405 : 200, -1);
            exchange.close();
        });
        recall.start();
        try {
            RecallBotClient client = clientOf(recall);
            client.leave("bot-1");
            client.remove("bot-2");
        } finally {
            recall.stop(0);
        }

        assertThat(asked).containsExactly("POST /api/v1/bot/bot-1/leave_call/", "DELETE /api/v1/bot/bot-2/",
                "POST /api/v1/bot/bot-2/leave_call/");
    }

    private static RecallBotClient clientOf(HttpServer recall) {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.resilience()).thenReturn(new ResilienceSettings(Duration.ofSeconds(2), 0,
                Duration.ofMillis(1), 1.0, Duration.ZERO, Duration.ofMillis(1), Duration.ofSeconds(1)));
        VendorRateLimiter limiter = new VendorRateLimiter();
        RecallSettings settings = new RecallSettings("recall-key", null, "bot-secret",
                "http://127.0.0.1:" + recall.getAddress().getPort(), Duration.ofMinutes(10), null,
                Duration.ofSeconds(15), 240);
        return new RecallBotClient(settings, new VendorClientFactory(properties), limiter,
                new VendorCallGuard(limiter, properties), RestClient.builder(), Clock.systemUTC());
    }

    private static HttpServer recallAnswering(int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static List<RecallBotNotice> notices(String body) {
        return RecallBotClient.noticesIn(body.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpServer storageServing(String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
