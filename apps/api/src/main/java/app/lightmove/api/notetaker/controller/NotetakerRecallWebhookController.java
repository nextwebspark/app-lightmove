package app.lightmove.api.notetaker.controller;

import app.lightmove.api.notetaker.service.NotetakerLiveFeed;
import app.lightmove.api.notetaker.service.NotetakerWebhook;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Recall.ai reports on a Notetaker bot. Public: the Svix signature over the raw body, under
 * {@code lightmove.recall.bot-webhook-secret}, is the whole credential, checked before anything is read. The live
 * transcript also takes the token in each copilot bot's own URL, which feeds that bot's recording alone.
 */
@RestController
@RequestMapping("/api/v1/notetaker/webhooks/recall")
@RequiredArgsConstructor
public class NotetakerRecallWebhookController {

    private final NotetakerWebhook webhook;
    private final NotetakerLiveFeed liveFeed;

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader(name = "webhook-id", required = false) String id,
                                        @RequestHeader(name = "webhook-timestamp", required = false) String timestamp,
                                        @RequestHeader(name = "webhook-signature", required = false) String signature,
                                        @RequestHeader(name = "svix-id", required = false) String svixId,
                                        @RequestHeader(name = "svix-timestamp", required = false) String svixTimestamp,
                                        @RequestHeader(name = "svix-signature", required = false) String svixSignature,
                                        @RequestBody(required = false) byte[] body) {
        webhook.receive(body == null ? new byte[0] : body, id != null ? id : svixId,
                timestamp != null ? timestamp : svixTimestamp, signature != null ? signature : svixSignature);
        return ResponseEntity.ok().build();
    }

    /** The live copilot's transcript as it is spoken: answered at once, the coach runs after, off this thread. */
    @PostMapping(path = "/live", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receiveLive(@RequestParam(name = "t", required = false) String token,
                                            @RequestHeader(name = "webhook-id", required = false) String id,
                                            @RequestHeader(name = "webhook-timestamp", required = false) String timestamp,
                                            @RequestHeader(name = "webhook-signature", required = false) String signature,
                                            @RequestHeader(name = "svix-id", required = false) String svixId,
                                            @RequestHeader(name = "svix-timestamp", required = false)
                                            String svixTimestamp,
                                            @RequestHeader(name = "svix-signature", required = false)
                                            String svixSignature,
                                            @RequestBody(required = false) byte[] body) {
        liveFeed.receive(body == null ? new byte[0] : body, token, id != null ? id : svixId,
                timestamp != null ? timestamp : svixTimestamp, signature != null ? signature : svixSignature);
        return ResponseEntity.ok().build();
    }
}
