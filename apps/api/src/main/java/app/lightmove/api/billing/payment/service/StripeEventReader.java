package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Verifies a Stripe webhook delivery's signature, then reads the event into a {@link PaymentEvent}. */
@Slf4j
@RequiredArgsConstructor
class StripeEventReader {

    static final String PACK_KEY = "pack";

    private static final Set<String> PERIOD_REASONS =
            Set.of("subscription_create", "subscription_cycle", "subscription_update");

    private final String webhookSecret;

    PaymentEvent read(byte[] payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || payload == null || signature == null) {
            throw ApiException.of(ErrorCode.BILLING_WEBHOOK_REJECTED);
        }
        Event event;
        try {
            event = Webhook.constructEvent(new String(payload, StandardCharsets.UTF_8), signature, webhookSecret);
        } catch (SignatureVerificationException forged) {
            throw ApiException.of(ErrorCode.BILLING_WEBHOOK_REJECTED);
        }
        Instant at = Instant.ofEpochSecond(event.getCreated());
        return switch (event.getType()) {
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                    new PaymentEvent.SubscriptionChanged(event.getId(), event.getType(), at,
                            stateOf((Subscription) objectOf(event)));
            case "invoice.paid" -> invoicePaid(event, at, (Invoice) objectOf(event));
            case "invoice.payment_failed" -> paymentFailed(event, at, (Invoice) objectOf(event));
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    creditsPaid(event, at, (Session) objectOf(event));
            default -> ignored(event, at);
        };
    }

    /** An event on an API version other than the pinned one is read anyway: every field read here exists on both. */
    private static StripeObject objectOf(Event event) {
        return event.getDataObjectDeserializer().getObject().orElseGet(() -> {
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException unreadable) {
                throw new IllegalStateException("Stripe event " + event.getId() + " could not be read", unreadable);
            }
        });
    }

    private static StripeSubscriptionState stateOf(Subscription subscription) {
        SubscriptionItem seat = subscription.getItems().getData().getFirst();
        return new StripeSubscriptionState(subscription.getCustomer(), subscription.getId(),
                statusOf(subscription.getStatus()), seat.getPrice().getId(), seat.getQuantity(),
                Instant.ofEpochSecond(seat.getCurrentPeriodStart()), Instant.ofEpochSecond(seat.getCurrentPeriodEnd()));
    }

    private static PaymentEvent invoicePaid(Event event, Instant at, Invoice invoice) {
        String subscriptionId = subscriptionOf(invoice);
        if (subscriptionId == null) {
            return ignored(event, at);
        }
        StripeSubscriptionState seats = seatLineOf(invoice)
                .map(line -> new StripeSubscriptionState(invoice.getCustomer(), subscriptionId,
                        SubscriptionStatus.ACTIVE, line.getPricing().getPriceDetails().getPrice(), line.getQuantity(),
                        Instant.ofEpochSecond(line.getPeriod().getStart()),
                        Instant.ofEpochSecond(line.getPeriod().getEnd())))
                .orElse(null);
        boolean startsPeriod = seats != null && PERIOD_REASONS.contains(invoice.getBillingReason());
        return new PaymentEvent.InvoicePaid(event.getId(), event.getType(), at, invoice.getId(), invoice.getCustomer(),
                subscriptionId, seats, startsPeriod);
    }

    private static PaymentEvent paymentFailed(Event event, Instant at, Invoice invoice) {
        String subscriptionId = subscriptionOf(invoice);
        if (subscriptionId == null) {
            return ignored(event, at);
        }
        return new PaymentEvent.InvoicePaymentFailed(event.getId(), event.getType(), at, invoice.getCustomer(),
                subscriptionId);
    }

    /** A subscription's own Checkout completes too; only a paid pack of ours is read. */
    private static PaymentEvent creditsPaid(Event event, Instant at, Session session) {
        Map<String, String> metadata = session.getMetadata() == null ? Map.of() : session.getMetadata();
        if (!"payment".equals(session.getMode()) || !"paid".equals(session.getPaymentStatus())
                || !metadata.containsKey(PACK_KEY)) {
            return ignored(event, at);
        }
        if (session.getAmountTotal() == null) {
            log.warn("Stripe event {} paid for pack {} with no amount; nothing granted", event.getId(),
                    metadata.get(PACK_KEY));
            return ignored(event, at);
        }
        long tax = session.getTotalDetails() == null || session.getTotalDetails().getAmountTax() == null
                ? 0 : session.getTotalDetails().getAmountTax();
        String paymentRef = session.getPaymentIntent() != null ? session.getPaymentIntent() : session.getId();
        return new PaymentEvent.CreditsPaid(event.getId(), event.getType(), at, session.getCustomer(), paymentRef,
                metadata.get(PACK_KEY), session.getAmountTotal() - tax);
    }

    private static String subscriptionOf(Invoice invoice) {
        if (invoice.getParent() == null || invoice.getParent().getSubscriptionDetails() == null) {
            return null;
        }
        return invoice.getParent().getSubscriptionDetails().getSubscription();
    }

    /** The line billing the seats for a period, as opposed to a proration of a change made during one. */
    private static Optional<InvoiceLineItem> seatLineOf(Invoice invoice) {
        if (invoice.getLines() == null) {
            return Optional.empty();
        }
        return invoice.getLines().getData().stream()
                .filter(line -> line.getParent() != null && line.getParent().getSubscriptionItemDetails() != null)
                .filter(line -> !Boolean.TRUE.equals(line.getParent().getSubscriptionItemDetails().getProration()))
                .filter(line -> line.getPricing() != null && line.getPricing().getPriceDetails() != null)
                .findFirst();
    }

    private static SubscriptionStatus statusOf(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due", "unpaid", "paused" -> SubscriptionStatus.PAST_DUE;
            case "canceled", "incomplete_expired" -> SubscriptionStatus.CANCELLED;
            default -> null;
        };
    }

    private static PaymentEvent ignored(Event event, Instant at) {
        return new PaymentEvent.Ignored(event.getId(), event.getType(), at);
    }
}
