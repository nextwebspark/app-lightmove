package app.lightmove.api.billing.payment.model;

import java.time.Instant;

/** A verified Stripe webhook event, read into what billing acts on; anything else is {@link Ignored}. */
public sealed interface PaymentEvent {

    String eventId();

    String type();

    /** When Stripe created the event: deliveries arrive in any order, and this is the order they happened in. */
    Instant createdAt();

    /** {@code customer.subscription.created}, {@code .updated} or {@code .deleted}. */
    record SubscriptionChanged(String eventId, String type, Instant createdAt, StripeSubscriptionState subscription)
            implements PaymentEvent {
    }

    /**
     * {@code invoice.paid} on a subscription.
     *
     * @param subscription the seat line's price, quantity and period; null on an invoice of prorations alone
     * @param startsPeriod whether the invoice opens a billing period, whose month of credits it pays for
     */
    record InvoicePaid(String eventId, String type, Instant createdAt, String invoiceId, String customerId,
                       String subscriptionId, StripeSubscriptionState subscription, boolean startsPeriod)
            implements PaymentEvent {
    }

    /** {@code invoice.payment_failed} on a subscription; Stripe sends one per attempt at the same invoice. */
    record InvoicePaymentFailed(String eventId, String type, Instant createdAt, String invoiceId, String customerId,
                                String subscriptionId) implements PaymentEvent {
    }

    /**
     * A paid checkout for a pack of contact credits; how many the pack holds is {@code lightmove.billing.packs}'.
     *
     * @param paymentRef the payment intent, which a grant is keyed on so a pack is granted once
     * @param netFils what was paid before VAT, in fils
     */
    record CreditsPaid(String eventId, String type, Instant createdAt, String customerId, String paymentRef,
                       String packCode, long netFils) implements PaymentEvent {
    }

    record Ignored(String eventId, String type, Instant createdAt) implements PaymentEvent {
    }
}
