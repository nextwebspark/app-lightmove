package app.lightmove.api.outreach.controller;

import app.lightmove.api.outreach.service.OutreachInboxService;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the mail service tells us a sender's mailbox heard back. Public — the mail service holds no
 * bearer token — so the delivery's signature is its whole credential, checked before anything is read.
 */
@RestController
@RequestMapping("/api/v1/outreach/webhooks/mailbox")
@RequiredArgsConstructor
public class OutreachWebhookController {

    /** The service proves it owns the URL by asking us to echo a value; anything but a plain token is not echoed. */
    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final OutreachInboxService inbox;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> challenge(@RequestParam(required = false) String challenge) {
        if (challenge == null || !CHALLENGE.matcher(challenge).matches()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader(name = "X-Nylas-Signature", required = false) String signature,
                                        @RequestBody(required = false) byte[] body) {
        inbox.receive(signature, body);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
