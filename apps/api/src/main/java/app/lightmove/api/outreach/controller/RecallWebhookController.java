package app.lightmove.api.outreach.controller;

import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import app.lightmove.api.outreach.service.RecallCalendars;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Recall.ai tells us a calendar it reads for a direct mailbox changed. Public — Recall holds no bearer
 * token — so the Svix signature over the raw body is the whole credential, checked before anything is read.
 * Svix sends its headers as {@code webhook-*}, and as {@code svix-*} on older endpoints.
 */
@RestController
@RequestMapping("/api/v1/outreach/webhooks/recall")
@RequiredArgsConstructor
public class RecallWebhookController {

    private final RecallCalendars calendars;

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader(name = "webhook-id", required = false) String id,
                                        @RequestHeader(name = "webhook-timestamp", required = false) String timestamp,
                                        @RequestHeader(name = "webhook-signature", required = false) String signature,
                                        @RequestHeader(name = "svix-id", required = false) String svixId,
                                        @RequestHeader(name = "svix-timestamp", required = false) String svixTimestamp,
                                        @RequestHeader(name = "svix-signature", required = false) String svixSignature,
                                        @RequestBody(required = false) byte[] body) {
        calendars.receive(new RecallWebhookDelivery(id != null ? id : svixId,
                timestamp != null ? timestamp : svixTimestamp,
                signature != null ? signature : svixSignature,
                body));
        return ResponseEntity.ok().build();
    }
}
