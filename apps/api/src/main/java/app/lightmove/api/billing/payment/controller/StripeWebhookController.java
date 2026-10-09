package app.lightmove.api.billing.payment.controller;

import app.lightmove.api.billing.payment.service.PaymentGateway;
import app.lightmove.api.billing.payment.service.StripeEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Stripe reports payments. Public — Stripe holds no bearer token — so the signature over the raw body is the
 * whole credential, checked before anything is read; a delivery that fails it is a 400 with nothing written.
 */
@RestController
@RequestMapping("/api/v1/billing/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentGateway gateway;
    private final StripeEventHandler handler;

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader(name = "Stripe-Signature", required = false) String signature,
                                        @RequestBody(required = false) byte[] body) {
        handler.handle(gateway.eventOf(body, signature));
        return ResponseEntity.ok().build();
    }
}
