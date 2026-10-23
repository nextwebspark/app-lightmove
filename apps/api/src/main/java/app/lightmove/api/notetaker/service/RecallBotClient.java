package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.webhook.service.SvixSignatures;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recall.ai's bot API (v1) over HTTP, {@code Authorization: Token <key>}: a bot sent at once or scheduled for the
 * call's start, with Recall's own transcription and diarisation, its consent line said in the chat as it joins, and our
 * ids on it. Its webhooks are Svix-signed, on an endpoint of their own ({@code lightmove.recall.bot-webhook-secret}).
 */
public class RecallBotClient implements RecallBotApi {

    static final String VENDOR = "recall";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final int REQUESTS_PER_SECOND = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RecallSettings config;
    private final RestClient client;
    private final RestClient downloads;
    private final VendorCallGuard guard;
    private final Clock clock;

    public RecallBotClient(RecallSettings config, VendorClientFactory clientFactory, VendorRateLimiter rateLimiter,
                           VendorCallGuard guard, RestClient.Builder builder, Clock clock) {
        this.config = config;
        this.guard = guard;
        this.clock = clock;
        this.client = clientFactory.create(VendorClientSpec.header(VENDOR, config.baseUrl(), "Authorization",
                "Token " + config.apiKey(), READ_TIMEOUT, REQUESTS_PER_SECOND), builder, rateLimiter);
        // The transcript's download link is a pre-signed file: fetched with no key, and never followed elsewhere.
        JdkClientHttpRequestFactory plain = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        plain.setReadTimeout(READ_TIMEOUT);
        this.downloads = RestClient.builder().requestFactory(plain).build();
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public String send(RecallBotRequest request) {
        JsonNode created = guard.call(VendorCall.of(VENDOR, "bot-create"), () -> client.post()
                .uri("/api/v1/bot/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyOf(request))
                .retrieve()
                .body(JsonNode.class));
        String id = created == null ? null : textOrNull(created.get("id"));
        if (id == null) {
            throw new VendorException(VendorCall.of(VENDOR, "bot-create"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return id;
    }

    /** Recall deletes only a bot that has not joined; one in the call (or past it) refuses, and is asked to leave. */
    @Override
    public void remove(String botId) {
        try {
            guard.call(VendorCall.of(VENDOR, "bot-delete"), () -> client.delete()
                    .uri("/api/v1/bot/{id}/", botId)
                    .retrieve()
                    .toBodilessEntity());
        } catch (VendorException refused) {
            if (refused.getKind() != VendorFailureKind.BAD_REQUEST && refused.getKind() != VendorFailureKind.GONE) {
                throw refused;
            }
            guard.call(VendorCall.of(VENDOR, "bot-leave"), () -> client.post()
                    .uri("/api/v1/bot/{id}/leave_call/", botId)
                    .retrieve()
                    .toBodilessEntity());
        }
    }

    @Override
    public RecallBotState state(String botId) {
        JsonNode bot = guard.call(VendorCall.of(VENDOR, "bot-read"), () -> client.get()
                .uri("/api/v1/bot/{id}/", botId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        if (bot == null) {
            throw new VendorException(VendorCall.of(VENDOR, "bot-read"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return stateOf(bot);
    }

    @Override
    public void deleteMedia(String botId) {
        guard.call(VendorCall.of(VENDOR, "bot-delete-media"), () -> client.post()
                .uri("/api/v1/bot/{id}/delete_media/", botId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public List<TranscriptLine> transcript(String transcriptId) {
        JsonNode transcript = guard.call(VendorCall.of(VENDOR, "transcript"), () -> client.get()
                .uri("/api/v1/transcript/{id}/", transcriptId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        URI download = downloadOf(transcript == null ? null : textOrNull(transcript.path("data").get("download_url")));
        if (download == null) {
            throw new VendorException(VendorCall.of(VENDOR, "transcript"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return linesOf(guard.call(VendorCall.of(VENDOR, "transcript-download"), () -> turnsAt(downloads, download)));
    }

    /**
     * Recall's storage serves the transcript as {@code application/octet-stream}, which no JSON converter accepts:
     * read as a JSON body, every transcript failed as malformed. Its bytes are parsed here instead. Anything but an
     * array of turns is malformed rather than an empty call, which would delete Recall's copy as "nothing heard".
     */
    static JsonNode turnsAt(RestClient downloads, URI download) {
        byte[] body = downloads.get().uri(download).retrieve().body(byte[].class);
        JsonNode turns;
        try {
            turns = body == null || body.length == 0 ? null : JSON.readTree(body);
        } catch (JacksonException unreadable) {
            throw new VendorException(VendorCall.of(VENDOR, "transcript-download"),
                    VendorFailureKind.MALFORMED_RESPONSE, unreadable);
        }
        if (turns == null || !turns.isArray()) {
            throw new VendorException(VendorCall.of(VENDOR, "transcript-download"),
                    VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return turns;
    }

    @Override
    public List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature) {
        if (!SvixSignatures.verifies(config.botWebhookSecret(), id, timestamp, signature, body, clock.instant())) {
            throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
        }
        return noticesIn(body);
    }

    /** Recall's pre-signed links are on its own hosts or AWS's storage; anything else is never fetched. */
    static URI downloadOf(String link) {
        if (link == null) {
            return null;
        }
        try {
            URI uri = URI.create(link);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean recallsOwn = host.endsWith(".recall.ai") || host.endsWith(".amazonaws.com");
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && recallsOwn ? uri : null;
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    /**
     * A turn per participant's run of words, at the time its first word was said. Recall names a participant by the
     * name they joined with; one it could not name keeps a number.
     */
    static List<TranscriptLine> linesOf(JsonNode turns) {
        List<TranscriptLine> lines = new ArrayList<>();
        if (turns == null || !turns.isArray()) {
            return lines;
        }
        for (JsonNode turn : turns) {
            JsonNode words = turn.path("words");
            if (!words.isArray() || words.isEmpty()) {
                continue;
            }
            Instant at = instantOrNull(textOrNull(words.get(0).path("start_timestamp").get("absolute")));
            StringBuilder text = new StringBuilder();
            for (JsonNode word : words) {
                String said = textOrNull(word.get("text"));
                if (said != null) {
                    text.append(text.isEmpty() ? "" : " ").append(said.strip());
                }
            }
            String name = textOrNull(turn.path("participant").get("name"));
            if (at == null || text.isEmpty()) {
                continue;
            }
            JsonNode participantId = turn.path("participant").get("id");
            lines.add(new TranscriptLine(at, name != null ? name
                    : "Speaker " + (participantId == null ? lines.size() + 1 : participantId.asText()),
                    text.toString(), name != null));
        }
        return lines;
    }

    /**
     * Both of Recall's webhook shapes: {@code bot.status_change} with the status beside the bot, and the per-status
     * events ({@code bot.in_call_recording}, …) with it under {@code data.data}.
     */
    public static List<RecallBotNotice> noticesIn(byte[] body) {
        JsonNode payload;
        try {
            payload = JSON.readTree(body);
        } catch (JacksonException unreadable) {
            return List.of();
        }
        if (payload == null) {
            return List.of();
        }
        String event = payload.path("event").asText("");
        JsonNode data = payload.path("data");
        String botId = textOrNull(data.path("bot").get("id"));
        if (botId == null) {
            return List.of();
        }
        if (event.equals("transcript.done")) {
            String transcriptId = textOrNull(data.path("transcript").get("id"));
            return transcriptId == null ? List.of() : List.of(new RecallBotNotice.TranscriptReady(botId, transcriptId));
        }
        if (event.equals("transcript.failed")) {
            return List.of(new RecallBotNotice.TranscriptFailed(botId));
        }
        if (!event.startsWith("bot.")) {
            return List.of();
        }
        JsonNode status = event.equals("bot.status_change") ? data.path("status") : data.path("data");
        String code = textOrNull(status.get("code"));
        if (code == null) {
            return List.of();
        }
        Instant at = instantOrNull(textOrNull(status.has("updated_at") ? status.get("updated_at")
                : status.get("created_at")));
        return List.of(new RecallBotNotice.StatusChanged(botId, code.toLowerCase(Locale.ROOT),
                textOrNull(status.get("sub_code")), at));
    }

    /** The latest of the bot's status changes, and the transcript of its latest recording, if any. */
    static RecallBotState stateOf(JsonNode bot) {
        JsonNode changes = bot.path("status_changes");
        JsonNode latest = changes.isArray() && !changes.isEmpty() ? changes.get(changes.size() - 1) : null;
        JsonNode recordings = bot.path("recordings");
        JsonNode transcript = recordings.isArray() && !recordings.isEmpty()
                ? recordings.get(recordings.size() - 1).path("media_shortcuts").path("transcript") : null;
        return new RecallBotState(
                latest == null ? null : lowerOrNull(textOrNull(latest.get("code"))),
                latest == null ? null : textOrNull(latest.get("sub_code")),
                latest == null ? null : instantOrNull(textOrNull(latest.get("created_at"))),
                transcript == null ? null : textOrNull(transcript.get("id")),
                transcript == null ? null : lowerOrNull(textOrNull(transcript.path("status").get("code"))));
    }

    /**
     * Recall refuses an {@code activate_after} below 1 with a 400, so a sent {@code 0} failed every bot; the
     * everyone-left timer leaves it out and runs from the start.
     */
    static final Map<String, Object> AUTOMATIC_LEAVE = Map.of(
            "waiting_room_timeout", seconds(Duration.ofMinutes(15)),
            "noone_joined_timeout", seconds(Duration.ofMinutes(15)),
            "everyone_left_timeout", Map.of("timeout", seconds(Duration.ofSeconds(30))),
            "in_call_not_recording_timeout", seconds(Duration.ofMinutes(10)),
            "silence_detection", Map.of(
                    "timeout", seconds(Duration.ofMinutes(10)), "activate_after", seconds(Duration.ofMinutes(10))),
            "bot_detection", Map.of("using_participant_events", Map.of(
                    "timeout", seconds(Duration.ofMinutes(2)), "activate_after", seconds(Duration.ofMinutes(10)))));

    private static int seconds(Duration duration) {
        return Math.toIntExact(duration.toSeconds());
    }

    static Map<String, Object> bodyOf(RecallBotRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meeting_url", request.meetingUrl());
        if (request.joinAt() != null) {
            body.put("join_at", request.joinAt().toString());
        }
        body.put("bot_name", request.botName());
        body.put("metadata", request.metadata());
        body.put("recording_config", Map.of(
                "transcript", Map.of(
                        "provider", Map.of("recallai_streaming", Map.of("mode", "prioritize_accuracy")),
                        "diarization", Map.of("use_separate_streams_when_available", true))));
        // Nobody is recorded without being told, by our bot or Recall's.
        body.put("chat", Map.of("on_bot_join", Map.of("send_to", "everyone", "message", request.consentLine())));
        // Recall's defaults keep a bot up to an hour in an empty or silent call, every minute of it billed.
        body.put("automatic_leave", AUTOMATIC_LEAVE);
        return body;
    }

    private static String lowerOrNull(String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Instant instantOrNull(String text) {
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
